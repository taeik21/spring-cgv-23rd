package com.ceos23.spring_boot.domain.movie.repository;

import com.ceos23.spring_boot.domain.movie.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MovieRepository extends JpaRepository<Movie, Long> {
    Boolean existsByTitleAndReleaseDate(String title, LocalDate releaseDate);

    Boolean existsByTitleAndReleaseDateAndDeletedAtIsNull(String title, LocalDate releaseDate);
    Optional<Movie> findByTitleAndReleaseDateAndDeletedAtIsNotNull(String title, LocalDate releaseDate);
    List<Movie> findAllByDeletedAtIsNull();
    Optional<Movie> findByIdAndDeletedAtIsNull(Long id);

    @Query(value = "SELECT * FROM movie WHERE MATCH(title) AGAINST(:keyword IN BOOLEAN MODE) AND deleted_at IS NULL", nativeQuery = true)
    List<Movie> searchByTitleFullText(@Param("keyword") String keyword);
}
