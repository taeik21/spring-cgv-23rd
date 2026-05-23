package com.ceos23.spring_boot.domain.movie.service;

import com.ceos23.spring_boot.domain.movie.dto.MovieCreateCommand;
import com.ceos23.spring_boot.domain.movie.dto.MovieInfo;
import com.ceos23.spring_boot.domain.movie.dto.MovieUpdateCommand;
import com.ceos23.spring_boot.domain.movie.entity.Movie;
import com.ceos23.spring_boot.domain.movie.repository.MovieRepository;
import com.ceos23.spring_boot.global.exception.BusinessException;
import com.ceos23.spring_boot.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieService {
    private final MovieRepository movieRepository;

    @Cacheable(value = "moviesAll", key = "'all'")
    public List<MovieInfo> findAllMovies() {
        List<Movie> movies = movieRepository.findAllByDeletedAtIsNull();
        return movies.stream()
                .map(MovieInfo::from)
                .toList();
    }

    public List<MovieInfo> searchMovies(String title) {
        List<Movie> movies = movieRepository.searchByTitleFullText(title);
        return movies.stream()
                .map(MovieInfo::from)
                .toList();
    }

    @Cacheable(value = "movies", key = "#id")
    public MovieInfo findMovie(Long id) {
        Movie movie = findMovieById(id);

        return MovieInfo.from(movie);
    }

    @Transactional
    @CacheEvict(value = "moviesAll", key = "'all'")
    public MovieInfo createMovie(MovieCreateCommand command) {
        if (movieRepository.existsByTitleAndReleaseDateAndDeletedAtIsNull(command.title(), command.releaseDate()))
            throw new BusinessException(ErrorCode.DUPLICATE_MOVIE);

        Optional<Movie> deletedMovie = movieRepository.findByTitleAndReleaseDateAndDeletedAtIsNotNull(command.title(), command.releaseDate());

        return deletedMovie
                .map(dm ->restoreMovie(dm, command))
                .orElseGet(() -> createNewMovie(command));
    }

    private MovieInfo restoreMovie(Movie deletedMovie, MovieCreateCommand command) {
        deletedMovie.restoreDelete();
        deletedMovie.update(
                command.title(),
                command.runtime(),
                command.releaseDate(),
                command.ageRating(),
                command.posterUrl(),
                command.description()
        );
        return MovieInfo.from(deletedMovie);
    }

    private MovieInfo createNewMovie(MovieCreateCommand command) {
        Movie movie = Movie.builder()
                .title(command.title())
                .runtime(command.runtime())
                .releaseDate(command.releaseDate())
                .ageRating(command.ageRating())
                .posterUrl(command.posterUrl())
                .description(command.description())
                .build();

        movieRepository.save(movie);

        return MovieInfo.from(movie);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "movies", key = "#id"),
            @CacheEvict(value = "moviesAll", key = "'all'")
    })
    public MovieInfo updateMovie(Long id, MovieUpdateCommand command) {
        Movie movie = findMovieById(id);

        if (movie.uniqueKeyChanged(command.title(), command.releaseDate())) {
            if (movieRepository.existsByTitleAndReleaseDate(command.title(), command.releaseDate()))
                throw new BusinessException(ErrorCode.DUPLICATE_MOVIE);
        }

        movie.update(
                command.title(),
                command.runtime(),
                command.releaseDate(),
                command.ageRating(),
                command.posterUrl(),
                command.description()
        );

        return MovieInfo.from(movie);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "movies", key = "#id"),
            @CacheEvict(value = "moviesAll", key = "'all'")
    })
    public void deleteMovie(Long id) {
        Movie movie = findMovieById(id);

        movie.softDelete();
    }

    private Movie findMovieById(Long id) {
        return movieRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
    }
}
