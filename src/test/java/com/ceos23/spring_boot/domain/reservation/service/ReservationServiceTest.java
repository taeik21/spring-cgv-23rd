package com.ceos23.spring_boot.domain.reservation.service;

import com.ceos23.spring_boot.domain.payment.service.PaymentService;
import com.ceos23.spring_boot.domain.reservation.dto.ReservationCreateCommand;
import com.ceos23.spring_boot.domain.reservation.dto.ReservationInfo;
import com.ceos23.spring_boot.domain.reservation.entity.Reservation;
import com.ceos23.spring_boot.domain.reservation.entity.ReservationStatus;
import com.ceos23.spring_boot.domain.reservation.entity.Schedule;
import com.ceos23.spring_boot.domain.reservation.repository.ReservationRepository;
import com.ceos23.spring_boot.domain.reservation.repository.ReservedSeatRepository;
import com.ceos23.spring_boot.domain.reservation.repository.ScheduleRepository;
import com.ceos23.spring_boot.domain.theater.entity.Screen;
import com.ceos23.spring_boot.domain.theater.entity.ScreenType;
import com.ceos23.spring_boot.domain.theater.entity.Seat;
import com.ceos23.spring_boot.domain.theater.entity.SeatGrade;
import com.ceos23.spring_boot.domain.theater.repository.SeatRepository;
import com.ceos23.spring_boot.domain.user.entity.User;
import com.ceos23.spring_boot.domain.user.repository.UserRepository;
import com.ceos23.spring_boot.global.exception.BusinessException;
import com.ceos23.spring_boot.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    @InjectMocks
    private ReservationService reservationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservedSeatRepository reservedSeatRepository;

    @Mock
    private PaymentService paymentService;

    @Test
    @DisplayName("예매 성공: 모든 조건이 맞으면 예매가 정상적으로 완료된다.")
    void createReservation_Success() {
        // Given
        String email = "test@test.com";
        Long screenId = 1L;
        Long scheduleId = 1L;
        List<Long> seatIds = List.of(10L, 11L);
        ReservationCreateCommand command = new ReservationCreateCommand(email, scheduleId, seatIds);

        User user = User.builder().build();

        ScreenType screenType = ScreenType.builder()
                .name("4DX")
                .surchargePrice(3000)
                .build();

        Screen screen = Screen.builder()
                .screenType(screenType)
                .build();
        ReflectionTestUtils.setField(screen, "id", screenId);

        Schedule schedule = Schedule.builder()
                .screen(screen)
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(1).plusHours(2))
                .basePrice(10000)
                .build();
        ReflectionTestUtils.setField(schedule, "id", scheduleId);

        SeatGrade seatGrade1 = SeatGrade.builder()
                .surchargePrice(2000)
                .build();

        SeatGrade seatGrade2 = SeatGrade.builder()
                .surchargePrice(0)
                .build();

        Seat seat1 = Seat.builder()
                .seatGrade(seatGrade1)
                .screen(screen)
                .rowName("A")
                .colNumber(1)
                .build();

        Seat seat2 = Seat.builder()
                .seatGrade(seatGrade2)
                .screen(screen)
                .rowName("A")
                .colNumber(2)
                .build();
        ReflectionTestUtils.setField(seat1, "id", 10L);
        ReflectionTestUtils.setField(seat2, "id", 11L);

        List<Seat> seats = List.of(seat1, seat2);

        given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.of(user));
        given(scheduleRepository.findByIdAndDeletedAtIsNull(scheduleId)).willReturn(Optional.of(schedule));
        given(seatRepository.findAllByIdInAndScreenIdAndDeletedAtIsNull(seatIds, screenId)).willReturn(seats);
        given(reservedSeatRepository.existsByScheduleIdAndSeatIdInAndReservationStatusIn(
                scheduleId, seatIds, List.of(ReservationStatus.PAID, ReservationStatus.PENDING))).willReturn(false);

        // When
        ReservationInfo result = reservationService.createReservation(command);

        // Then
        assertThat(result.totalPrice()).isEqualTo(28000);
        assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(result.scheduleId()).isEqualTo(scheduleId);
        assertThat(result.reservedSeatIds()).containsExactly(10L, 11L);
    }

    @Test
    @DisplayName("예매 실패: 존재하지 않는 유저일 경우 예외가 발생한다.")
    void createReservation_Fail_UserNotFound() {
        // Given
        String email = "notfound@test.com";
        ReservationCreateCommand command = new ReservationCreateCommand(email, 1L, List.of(10L));
        given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.empty());

        // When, Then
        assertThatThrownBy(() -> reservationService.createReservation(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("예매 실패: 존재하지 않는 상영 일정일 경우 예외가 발생한다.")
    void createReservation_Fail_ScheduleNotFound() {
        // Given
        String email = "test@test.com";
        ReservationCreateCommand command = new ReservationCreateCommand(email, 1L, List.of(10L));
        User user = User.builder().build();

        given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.of(user));
        given(scheduleRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        // When, Then
        assertThatThrownBy(() -> reservationService.createReservation(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SCHEDULE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("예매 실패: 요청한 좌석 중 일부가 DB에 존재하지 않으면 예외가 발생한다.")
    void createReservation_Fail_SeatNotFound() {
        // Given
        String email = "test@test.com";
        ReservationCreateCommand command = new ReservationCreateCommand(email, 1L, List.of(10L, 11L));
        User user = User.builder().build();

        Screen screen = Screen.builder().build();
        ReflectionTestUtils.setField(screen, "id", 1L);

        Schedule schedule = Schedule.builder()
                .screen(screen)
                .startTime(LocalDateTime.now().plusDays(1))
                .build();
        Seat seat1 = Seat.builder().screen(screen).build();

        given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.of(user));
        given(scheduleRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(schedule));
        given(seatRepository.findAllByIdInAndScreenIdAndDeletedAtIsNull(command.seatIds(), 1L)).willReturn(List.of(seat1));

        // When, Then
        assertThatThrownBy(() -> reservationService.createReservation(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.INVALID_SEAT.getMessage());
    }

    @Test
    @DisplayName("예매 실패: 선택한 좌석 중 이미 예매된 좌석이 포함되어 있으면 예외가 발생한다.")
    void createReservation_Fail_AlreadyReserved() {
        // Given
        String email = "test@test.com";
        Long scheduleId = 1L;
        List<Long> seatIds = List.of(10L, 11L);
        ReservationCreateCommand command = new ReservationCreateCommand(email, scheduleId, seatIds);

        User user = User.builder().build();

        Screen screen = Screen.builder().build();
        ReflectionTestUtils.setField(screen, "id", 1L);

        Schedule schedule = Schedule.builder()
                .screen(screen)
                .startTime(LocalDateTime.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(schedule, "id", scheduleId);

        Seat seat1 = Seat.builder().build();
        Seat seat2 = Seat.builder().build();
        List<Seat> seats = List.of(seat1, seat2);

        given(userRepository.findByEmailAndDeletedAtIsNull(email)).willReturn(Optional.of(user));
        given(scheduleRepository.findByIdAndDeletedAtIsNull(scheduleId)).willReturn(Optional.of(schedule));
        given(seatRepository.findAllByIdInAndScreenIdAndDeletedAtIsNull(seatIds, 1L)).willReturn(seats);
        given(reservedSeatRepository.existsByScheduleIdAndSeatIdInAndReservationStatusIn(
                scheduleId, seatIds, List.of(ReservationStatus.PAID, ReservationStatus.PENDING))).willReturn(true);

        // When, Then
        assertThatThrownBy(() -> reservationService.createReservation(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.SEAT_ALREADY_RESERVED.getMessage());
    }

    @Test
    @DisplayName("예매 취소 성공: 유효한 결제 ID로 예약이 정상적으로 취소된다.")
    void cancelReservation_Success() {
        // Given
        String paymentId = "20240101_abcd1234";

        User user = User.builder().build();
        Schedule schedule = Schedule.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .build();

        Reservation reservation = Reservation.builder()
                .paymentId(paymentId)
                .user(user)
                .schedule(schedule)
                .status(ReservationStatus.PENDING)
                .build();

        given(reservationRepository.findByPaymentId(paymentId)).willReturn(Optional.of(reservation));

        // When
        reservationService.cancelReservation(paymentId);

        // Then
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELED);
    }

    @Test
    @DisplayName("예매 취소 실패: 존재하지 않는 결제 ID면 예외가 발생한다.")
    void cancelReservation_Fail_NotFound() {
        // Given
        String invalidPaymentId = "20240101_notexist";
        given(reservationRepository.findByPaymentId(invalidPaymentId)).willReturn(Optional.empty());

        // When, Then
        assertThatThrownBy(() -> reservationService.cancelReservation(invalidPaymentId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.RESERVATION_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("예매 취소 실패: 이미 취소된 예매 내역을 다시 취소하려고 하면 예외가 발생한다.")
    void cancelReservation_Fail_AlreadyCanceled() {
        // Given
        String paymentId = "20240101_abcd1234";

        User user = User.builder().build();
        Schedule schedule = Schedule.builder()
                .startTime(LocalDateTime.now().plusDays(1))
                .build();

        Reservation reservation = Reservation.builder()
                .paymentId(paymentId)
                .user(user)
                .schedule(schedule)
                .status(ReservationStatus.CANCELED)
                .build();

        given(reservationRepository.findByPaymentId(paymentId)).willReturn(Optional.of(reservation));

        // When, Then
        assertThatThrownBy(() -> reservationService.cancelReservation(paymentId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.ALREADY_CANCELED_RESERVATION.getMessage());
    }
}