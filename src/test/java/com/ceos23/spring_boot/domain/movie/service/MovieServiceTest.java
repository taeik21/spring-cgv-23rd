package com.ceos23.spring_boot.domain.movie.service;

import com.ceos23.spring_boot.domain.movie.dto.MovieCreateCommand;
import com.ceos23.spring_boot.domain.movie.dto.MovieInfo;
import com.ceos23.spring_boot.domain.movie.dto.MovieSearchCommand;
import com.ceos23.spring_boot.domain.movie.dto.MovieUpdateCommand;
import com.ceos23.spring_boot.domain.movie.entity.Movie;
import com.ceos23.spring_boot.domain.movie.repository.MovieRepository;
import com.ceos23.spring_boot.global.exception.BusinessException;
import com.ceos23.spring_boot.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieService movieService;

    @Test
    @DisplayName("영화 전체 조회: 검색 조건이 없으면 findAll이 호출된다.")
    void findMovies_findAll() {
        // Given
        MovieSearchCommand command = new MovieSearchCommand(null);
        Movie movie1 = Movie.builder()
                .title("인셉션")
                .runtime(120)
                .releaseDate(LocalDate.of(2001, 1, 1))
                .ageRating("12세")
                .build();
        Movie movie2 = Movie.builder()
                .title("인터스텔라")
                .runtime(200)
                .releaseDate(LocalDate.of(2011, 1, 1))
                .ageRating("15세")
                .build();

        given(movieRepository.findAllByDeletedAtIsNull()).willReturn(List.of(movie1, movie2));

        // When
        List<MovieInfo> result = movieService.findAllMovies();

        // Then
        assertThat(result).hasSize(2);
        verify(movieRepository).findAllByDeletedAtIsNull();
        verify(movieRepository, never()).findByTitleContainingAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("영화 조건 조회: 검색 조건이 있으면 findByTitleContaining이 호출된다.")
    void findMovies_WithTitle() {
        // Given
        String keyword = "인셉";
        MovieSearchCommand command = new MovieSearchCommand(keyword);
        Movie movie = Movie.builder()
                .title("인셉션")
                .runtime(120)
                .releaseDate(LocalDate.of(2001, 1, 1))
                .ageRating("12세")
                .build();

        given(movieRepository.findByTitleContainingAndDeletedAtIsNull(keyword)).willReturn(List.of(movie));

        // When
        List<MovieInfo> result = movieService.searchMovies(keyword);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("인셉션");

        verify(movieRepository).findByTitleContainingAndDeletedAtIsNull(keyword);
        verify(movieRepository, never()).findAll();
    }

    @Test
    @DisplayName("영화 단건 조회 성공: 존재하는 ID면 정상적으로 단건조회가 실행된다.")
    void findMovie_Success() {
        // Given
        Long validId = 1L;
        Movie movie = Movie.builder()
                .title("인셉션")
                .runtime(120)
                .releaseDate(LocalDate.of(2001, 1, 1))
                .ageRating("12세")
                .build();

        given(movieRepository.findByIdAndDeletedAtIsNull(validId)).willReturn(Optional.of(movie));

        // When
        MovieInfo result = movieService.findMovie(validId);

        // Then
        assertThat(result.title()).isEqualTo("인셉션");
    }

    @Test
    @DisplayName("영화 단건 조회 실패: 존재하지 않는 ID면 예외가 발생한다.")
    void findMovie_Fail() {
        // Given
        Long invalidId = 1L;
        given(movieRepository.findByIdAndDeletedAtIsNull(invalidId)).willReturn(Optional.empty());

        // When, Then
        assertThatThrownBy(() -> movieService.findMovie(invalidId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.MOVIE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("영화 생성: 입력받은 정보로 영화를 생성하고 반환한다.")
    void createMovie() {
        // Given
        MovieCreateCommand command = new MovieCreateCommand(
                "인셉션", 120, LocalDate.of(2001, 1, 1), "12세", "url", "줄거리"
        );

        // When
        MovieInfo result = movieService.createMovie(command);

        // Then
        assertThat(result.title()).isEqualTo("인셉션");
        assertThat(result.runtime()).isEqualTo(120);
    }

    @Test
    @DisplayName("영화 수정: 존재하는 ID면 정보를 수정하고 반환한다.")
    void updateMovie() {
        // Given
        Long movieId = 1L;
        Movie existingMovie = Movie.builder()
                .title("인셉션")
                .runtime(120)
                .releaseDate(LocalDate.of(2001, 1, 1))
                .ageRating("12세")
                .build();

        MovieUpdateCommand command = new MovieUpdateCommand(
                "인터스텔라", 200, LocalDate.now(), "15세", "new_url", "새로운 줄거리"
        );

        given(movieRepository.findByIdAndDeletedAtIsNull(movieId)).willReturn(Optional.of(existingMovie));

        // When
        MovieInfo result = movieService.updateMovie(movieId, command);

        // Then
        assertThat(result.title()).isEqualTo("인터스텔라");
        assertThat(result.runtime()).isEqualTo(200);
        assertThat(result.ageRating()).isEqualTo("15세");
    }

    @Test
    @DisplayName("영화 삭제 성공: 존재하는 ID면 삭제 로직을 수행한다.")
    void deleteMovie_Success() {
        // Given
        Long movieId = 1L;
        Movie movie = Movie.builder()
                .title("인셉션")
                .runtime(120)
                .releaseDate(LocalDate.of(2001, 1, 1))
                .ageRating("12세")
                .build();

        given(movieRepository.findByIdAndDeletedAtIsNull(movieId)).willReturn(Optional.of(movie));

        // When
        movieService.deleteMovie(movieId);

        // Then
        assertThat(movie.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("영화 삭제 실패: 존재하지 않는 ID면 삭제되지 않고 예외가 발생한다.")
    void deleteMovie_Fail() {
        // Given
        Long invalidId = 1L;
        given(movieRepository.findByIdAndDeletedAtIsNull(invalidId)).willReturn(Optional.empty());

        // When, Then
        assertThatThrownBy(() -> movieService.deleteMovie(invalidId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.MOVIE_NOT_FOUND.getMessage());
    }
}