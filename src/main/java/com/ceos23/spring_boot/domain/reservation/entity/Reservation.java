package com.ceos23.spring_boot.domain.reservation.entity;

import com.ceos23.spring_boot.domain.theater.entity.Seat;
import com.ceos23.spring_boot.domain.user.entity.User;
import com.ceos23.spring_boot.global.common.BaseSoftDeleteEntity;
import com.ceos23.spring_boot.global.common.BaseTimeEntity;
import com.ceos23.spring_boot.global.exception.BusinessException;
import com.ceos23.spring_boot.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Table(
    uniqueConstraints = {
        @UniqueConstraint(name = "UQ_PAYMENT_ID", columnNames = {"payment_id"})
    },
    indexes = {
        @Index(name = "idx_reservation_status_created_at", columnList = "status, created_at")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 50)
    private String paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "total_price")
    private Integer totalPrice;

    @Column(name = "order_name")
    private String orderName;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservedSeat> reservedSeats = new ArrayList<>();

    @Builder
    public Reservation(String paymentId, User user, Schedule schedule, ReservationStatus status, Integer totalPrice, String orderName) {
        this.paymentId = paymentId;
        this.user = user;
        this.schedule = schedule;
        this.status = status;
        this.totalPrice = totalPrice;
        this.orderName = orderName;
    }

    public static Reservation create(User user, Schedule schedule, List<Seat> seats, String orderName) {
        String paymentId = generatePaymentId();

        Reservation reservation = Reservation.builder()
                .paymentId(paymentId)
                .user(user)
                .schedule(schedule)
                .status(ReservationStatus.PENDING)
                .orderName(orderName)
                .build();

        int totalPrice = 0;
        for (Seat seat : seats) {
            ReservedSeat reservedSeat = ReservedSeat.create(reservation, schedule, seat);
            reservation.addReservedSeat(reservedSeat);

            totalPrice += reservedSeat.getPrice();
        }

        reservation.totalPrice = totalPrice;
        return reservation;
    }

    private static String generatePaymentId() {
        String prefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "_" + uuid;
    }

    private void addReservedSeat(ReservedSeat reservedSeat) {
        this.reservedSeats.add(reservedSeat);
        reservedSeat.updateReservation(this);
    }


    public void validateCancelable(){
        if (this.status == ReservationStatus.CANCELED)
            throw new BusinessException(ErrorCode.ALREADY_CANCELED_RESERVATION);

        LocalDateTime currentTime = LocalDateTime.now();

        if (currentTime.isAfter(schedule.getStartTime().minusMinutes(15)))
            throw new BusinessException(ErrorCode.CANCELLATION_DEADLINE_PASSED);
    }

    public void completePayment() {
        if (this.status != ReservationStatus.PENDING)
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATUS);

        this.status = ReservationStatus.PAID;
    }

    public void cancel() {
        if (this.status == ReservationStatus.CANCELED)
            throw new BusinessException(ErrorCode.ALREADY_CANCELED_RESERVATION);
        this.status = ReservationStatus.CANCELED;

        this.reservedSeats.clear();
    }

    public boolean isCanceled() {
        return this.status == ReservationStatus.CANCELED;
    }

    public void validatePendingStatus() {
        if (this.status != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_RESERVATION_STATUS);
        }
    }
}
