package com.ceos23.spring_boot.global.init;

import com.ceos23.spring_boot.domain.movie.entity.Movie;
import com.ceos23.spring_boot.domain.movie.repository.MovieRepository;
import com.ceos23.spring_boot.domain.reservation.entity.Schedule;
import com.ceos23.spring_boot.domain.reservation.repository.ScheduleRepository;
import com.ceos23.spring_boot.domain.store.entity.Inventory;
import com.ceos23.spring_boot.domain.store.entity.Menu;
import com.ceos23.spring_boot.domain.store.repository.InventoryRepository;
import com.ceos23.spring_boot.domain.store.repository.MenuRepository;
import com.ceos23.spring_boot.domain.theater.dto.ScreenCreateCommand;
import com.ceos23.spring_boot.domain.theater.entity.*;
import com.ceos23.spring_boot.domain.theater.repository.*;
import com.ceos23.spring_boot.domain.theater.service.ScreenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Profile({"local", "prod"})
@RequiredArgsConstructor
public class MasterDataInitializer implements CommandLineRunner {

    private final TheaterRepository theaterRepository;
    private final ScreenTypeRepository screenTypeRepository;
    private final SeatGradeRepository seatGradeRepository;
    private final SeatTemplateRepository seatTemplateRepository;
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final ScheduleRepository scheduleRepository;
    private final MenuRepository menuRepository;
    private final InventoryRepository inventoryRepository;

    private final ScreenService screenService;

    @Override
    @Transactional
    public void run(String... args) {
        // 이미 데이터가 세팅되어 있다면 중복 실행 방지
        if (theaterRepository.count() > 0) {
            log.info("기초 데이터가 이미 존재합니다. 초기화를 건너뜁니다.");
            return;
        }

        log.info("로컬 테스트용 마스터 데이터 초기화를 시작합니다.");

        // 1. [독립 데이터] 극장(Theater) 생성
        Theater theater = Theater.builder()
                .name("CGV 강남점")
                .location("서울")
                .build();
        theaterRepository.save(theater);

        Theater theater2 = Theater.builder()
                .name("CGV 제주점")
                .location("서귀포")
                .build();
        theaterRepository.save(theater2);

        // 2. [독립 데이터] 상영관 타입(ScreenType) 생성
        ScreenType standardType = ScreenType.builder().name("STANDARD").surchargePrice(0).build();
        ScreenType imaxType = ScreenType.builder().name("IMAX").surchargePrice(5000).build();
        ScreenType threeDType = ScreenType.builder().name("3D").surchargePrice(5000).build();
        screenTypeRepository.saveAll(List.of(standardType, imaxType, threeDType));

        // 3. [독립 데이터] 좌석 등급(SeatGrade) 생성
        SeatGrade economy = SeatGrade.builder().name("ECONOMY").surchargePrice(0).build();
        SeatGrade vip = SeatGrade.builder().name("VIP").surchargePrice(3000).build();
        seatGradeRepository.saveAll(List.of(economy, vip));

        // 4. [독립 데이터] 영화(Movie) 생성
        Movie movie = Movie.builder()
                .title("듄")
                .runtime(166)
                .releaseDate(LocalDate.of(2024, 2, 28))
                .ageRating("12세 이상 관람가")
                .build();
        movieRepository.save(movie);

        Movie movie2 = Movie.builder()
                .title("미션임파서블")
                .runtime(140)
                .releaseDate(LocalDate.of(2023, 12, 1))
                .ageRating("15세 이상 관람가")
                .build();
        movieRepository.save(movie2);

        // 5. [독립 데이터] 메뉴(Menu) 생성
        Menu popcorn = Menu.builder().name("고소 팝콘 (S)").price(4500).build();
        Menu popcornL = Menu.builder().name("고소 팝콘 (L)").price(5500).build();

        Menu cola = Menu.builder().name("코카콜라 (M)").price(2500).build();
        Menu colaL = Menu.builder().name("코카콜라 (L)").price(3000).build();
        menuRepository.saveAll(List.of(popcorn, popcornL, cola, colaL));

        // [연관 데이터]
        // 6. 재고(Inventory) 세팅
        Inventory popcornInventory = Inventory.builder().theater(theater).menu(popcorn).stock(10).build();
        Inventory popcornLInventory = Inventory.builder().theater(theater).menu(popcornL).stock(20).build();

        Inventory colaInventory = Inventory.builder().theater(theater).menu(cola).stock(20).build();
        Inventory colaLInventory = Inventory.builder().theater(theater).menu(colaL).stock(30).build();
        inventoryRepository.saveAll(List.of(popcornInventory, popcornLInventory, colaInventory, colaLInventory));

        // 7. 좌석 템플릿(SeatTemplate) 세팅
        createSeats(standardType, 'T', 20, economy, vip); // A~T열, 20번까지 (20x20)
        createSeats(imaxType, 'T', 20, economy, vip);     // A~T열, 20번까지 (20x20)
        createSeats(threeDType, 'O', 15, economy, vip);   // A~O열, 15번까지 (15x15)

        // 8. 상영관(Screen) 생성
        List<Theater> theaters = List.of(theater, theater2);
        List<ScreenType> screenTypes = List.of(standardType, imaxType, threeDType);
        List<Movie> movies = List.of(movie, movie2);

        // 극장별로 순회
        for (Theater t : theaters) {
            int screenNumber = 1;
            // 상영관 타입별로 순회
            for (ScreenType type : screenTypes) {

                // 1. 상영관 및 좌석 생성
                String screenName = type.getName() + "관"; // "STANDARD관", "IMAX관"
                screenService.createScreenWithSeats(
                        new ScreenCreateCommand(t.getId(), type.getId(), screenName)
                );

                // 2. 방금 생성된 상영관 엔티티 조회
                // Theater와 상영관 이름으로 정확히 찾아옵니다.
                Screen savedScreen = screenRepository.findByNameAndTheater(screenName, t)
                        .orElseThrow(() -> new RuntimeException("상영관 생성 실패"));

                // 3. 해당 상영관에 대해 모든 영화의 스케줄 생성
                LocalDateTime startTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0);

                for (Movie m : movies) {
                    Schedule schedule = Schedule.builder()
                            .movie(m)
                            .screen(savedScreen)
                            .startTime(startTime)
                            .endTime(startTime.plusMinutes(m.getRuntime()))
                            .basePrice(15000)
                            .build();
                    scheduleRepository.save(schedule);

                    startTime = startTime.plusHours(4);
                }

                screenNumber++;
            }
        }

        log.info("마스터 데이터 초기화 완료!");
    }

    // 좌석 생성 메서드
    private void createSeats(ScreenType type, char maxRow, int maxCol,SeatGrade economy, SeatGrade vip) {
        List<SeatTemplate> templates = new ArrayList<>();

        for (char rowChar = 'A'; rowChar <= maxRow; rowChar++) {
            String rowName = String.valueOf(rowChar);
            SeatGrade currentGrade = (rowChar <= 'E') ? economy : vip;

            for (int colNumber = 1; colNumber <= maxCol; colNumber++) {
                templates.add(SeatTemplate.builder()
                        .screenType(type)
                        .seatGrade(currentGrade)
                        .rowName(rowName)
                        .colNumber(colNumber)
                        .build());
            }
        }
        seatTemplateRepository.saveAll(templates);
    }
}