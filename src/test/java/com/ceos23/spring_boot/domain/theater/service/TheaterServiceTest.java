package com.ceos23.spring_boot.domain.theater.service;

import com.ceos23.spring_boot.domain.theater.dto.TheaterCreateCommand;
import com.ceos23.spring_boot.domain.theater.dto.TheaterInfo;
import com.ceos23.spring_boot.domain.theater.dto.TheaterSearchCommand;
import com.ceos23.spring_boot.domain.theater.dto.TheaterUpdateCommand;
import com.ceos23.spring_boot.domain.theater.entity.Theater;
import com.ceos23.spring_boot.domain.theater.repository.TheaterRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ceos23.spring_boot.global.exception.BusinessException;
import com.ceos23.spring_boot.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class TheaterServiceTest {
    @InjectMocks
    private TheaterService theaterService;

    @Mock
    private TheaterRepository theaterRepository;

    @Test
    @DisplayName("영화관 전체 조회: location 쿼리 파라미터가 없으면 전체조회가 실행된다.")
    void findTheaters_findAll() {
        //given
        TheaterSearchCommand command = new TheaterSearchCommand(null);

        Theater theater1 = Theater.builder()
                .name("CGV 강남")
                .location("서울")
                .build();
        Theater theater2 = Theater.builder()
                .name("CGV 서면")
                .location("부산")
                .build();

        given(theaterRepository.findAllByDeletedAtIsNull()).willReturn(List.of(theater1, theater2));

        //when
        List<TheaterInfo> result = theaterService.findAllTheaters();

        //then
        assertThat(result.size()).isEqualTo(2);
        assertThat(result).extracting("name")
                .containsExactlyInAnyOrder("CGV 강남", "CGV 서면");
        assertThat(result).extracting("location")
                .containsExactlyInAnyOrder("서울", "부산");

        verify(theaterRepository).findAllByDeletedAtIsNull();
        verify(theaterRepository, never()).findByLocationAndDeletedAtIsNull(command.location());
    }

    @Test
    @DisplayName("영화관 조건 조회: location 쿼리 파라미터가 없으면 조건 조회가 실행된다.")
    void findTheaters_findByLocation() {
        //given
        String location = "서울";
        TheaterSearchCommand command = new TheaterSearchCommand(location);

        Theater theater1 = Theater.builder()
                .name("CGV 강남")
                .location("서울")
                .build();
        Theater theater2 = Theater.builder()
                .name("CGV 서면")
                .location("부산")
                .build();

        given(theaterRepository.findByLocationAndDeletedAtIsNull(location)).willReturn(List.of(theater1));

        //when
        List<TheaterInfo> result = theaterService.searchTheaters(location);

        //then
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.getFirst().name()).isEqualTo("CGV 강남");
        assertThat(result.getFirst().location()).isEqualTo(location);

        verify(theaterRepository).findByLocationAndDeletedAtIsNull(location);
        verify(theaterRepository, never()).findAll();
    }

    @Test
    @DisplayName("영화관 단건 조회 성공: 존재하는 ID면 단건 조회가 실행된다.")
    void findTheater_Success() {
        //given
        Long validId = 1L;

        Theater theater = Theater.builder()
                .name("CGV 강남점")
                .location("서울")
                .build();

        given(theaterRepository.findByIdAndDeletedAtIsNull(validId)).willReturn(Optional.of(theater));

        //when
        TheaterInfo result = theaterService.findTheater(validId);

        //then
        assertThat(result.name()).isEqualTo("CGV 강남점");
        assertThat(result.location()).isEqualTo("서울");
    }

    @Test
    @DisplayName("영화관 단건 조회 실패: 존재하지 않는 ID면 예외가 발생한다.")
    void findTheater_Fail() {
        //given
        Long invalidId = 1L;

        given(theaterRepository.findByIdAndDeletedAtIsNull(invalidId)).willReturn(Optional.empty());

        //when, then
        assertThatThrownBy(()-> theaterService.findTheater(invalidId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.THEATER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("영화관 생성 성공: 중복되지 않은 이름이면 정상적으로 생성된다.")
    void createTheater_Success() {
        //given
        TheaterCreateCommand command = new TheaterCreateCommand("CGV 강남점", "서울");
        given(theaterRepository.existsByNameAndDeletedAtIsNull(command.name())).willReturn(false);

        //when
        TheaterInfo result = theaterService.createTheater(command);

        //then
        assertThat(result.name()).isEqualTo("CGV 강남점");
        assertThat(result.location()).isEqualTo("서울");
    }

    @Test
    @DisplayName("영화관 생성 실패: 중복되는 이름이면 예외가 발생한다.")
    void createTheater_Fail() {
        //given
        TheaterCreateCommand command = new TheaterCreateCommand("CGV 강남점", "서울");
        given(theaterRepository.existsByNameAndDeletedAtIsNull(command.name())).willReturn(true);

        //when, then
        assertThatThrownBy(()-> theaterService.createTheater(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DUPLICATE_THEATER_NAME.getMessage());
    }

    @Test
    @DisplayName("영화관 수정 성공: 존재하는 ID이고, 중복되지 않은 이름이면 정상적으로 변경된다.")
    void updateTheater_Success() {
        //given
        Long validId = 1L;
        TheaterUpdateCommand command = new TheaterUpdateCommand("CGV 강남점", "서울");

        Theater theater = Theater.builder()
                .name("CGV 판교점")
                .location("경기")
                .build();

        given(theaterRepository.findByIdAndDeletedAtIsNull(validId)).willReturn(Optional.of(theater));

        given(theaterRepository.existsByNameAndDeletedAtIsNull("CGV 강남점")).willReturn(false);

        //when
        theaterService.updateTheater(validId, command);

        //then
        assertThat(theater.getName()).isEqualTo("CGV 강남점");
        assertThat(theater.getLocation()).isEqualTo("서울");
    }

    @Test
    @DisplayName("영화관 수정 실패: 존재하지 않는 ID면 예외가 발생된다.")
    void updateTheater_Fail_InvalidId() {
        //given
        Long invalidId = 1L;
        given(theaterRepository.findByIdAndDeletedAtIsNull(invalidId)).willReturn(Optional.empty());

        TheaterUpdateCommand command = new TheaterUpdateCommand("CGV 강남점", "서울");

        //when, then
        assertThatThrownBy(()-> theaterService.updateTheater(invalidId, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.THEATER_NOT_FOUND.getMessage());

    }

    @Test
    @DisplayName("영화관 수정 실패: 중복된 이름이면 예외가 발생된다.")
    void updateTheater_Fail_DuplicateName() {
        //given
        Long validId = 1L;
        TheaterUpdateCommand command = new TheaterUpdateCommand("CGV 강남점", "서울");

        Theater theater = Theater.builder()
                .name("CGV 판교점")
                .location("경기")
                .build();

        given(theaterRepository.findByIdAndDeletedAtIsNull(validId)).willReturn(Optional.of(theater));

        given(theaterRepository.existsByNameAndDeletedAtIsNull("CGV 강남점")).willReturn(true);

        //when, then
        assertThatThrownBy(()-> theaterService.updateTheater(validId, command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.DUPLICATE_THEATER_NAME.getMessage());
    }

    @Test
    @DisplayName("영화관 삭제 성공: 존재하는 ID면 정상적으로 삭제된다.")
    void deleteTheater_Success() {
        //given
        Long validId = 1L;

        Theater theater = Theater.builder()
                .name("CGV 강남점")
                .location("서울")
                .build();

        given(theaterRepository.findByIdAndDeletedAtIsNull(validId)).willReturn(Optional.of(theater));

        //when
        theaterService.deleteTheater(validId);

        // then
        assertThat(theater.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("영화관 삭제 실패: 존재하지 않는 ID면 예외가 발생한다.")
    void deleteTheater_Fail() {
        //given
        Long invalidId = 1L;

        given(theaterRepository.findByIdAndDeletedAtIsNull(invalidId)).willReturn(Optional.empty());

        //when, then
        assertThatThrownBy(()-> theaterService.deleteTheater(invalidId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.THEATER_NOT_FOUND.getMessage());
    }
}