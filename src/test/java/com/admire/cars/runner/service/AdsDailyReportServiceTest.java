package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsDailyStat;
import com.admire.cars.runner.entity.AdsSearchTermStat;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsCampaignSnapshotRepository;
import com.admire.cars.runner.repository.AdsDailyStatRepository;
import com.admire.cars.runner.repository.AdsSearchTermStatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdsDailyReportServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private AdsDailyStatRepository dailyStatRepository;
    @Mock
    private AdsCampaignSnapshotRepository snapshotRepository;
    @Mock
    private AdsSearchTermStatRepository searchTermRepository;

    private AdsDailyReportService service;

    @BeforeEach
    void setUp() {
        service = new AdsDailyReportService(userService, dailyStatRepository, snapshotRepository, searchTermRepository);
        User user = new User();
        user.setUserPhoneNumber("13800000000");
        when(userService.getEnabledUserByApiKey("key")).thenReturn(user);
    }

    @Test
    @SuppressWarnings("unchecked")
    void overwritesExistingDailyRowAndSkipsInvalidDates() {
        AdsDailyStat existing = new AdsDailyStat();
        existing.setAdsOwner("13800000000");
        existing.setCampaignName("brand");
        existing.setStatDate(LocalDate.of(2026, 11, 12));
        existing.setNetwork("SEARCH");
        existing.setClicks(3L);
        when(dailyStatRepository.findSince(eq("13800000000"), any())).thenReturn(List.of(existing));

        AdsDailyReportService.DailyReportRequest request = new AdsDailyReportService.DailyReportRequest(
                "cny", "2026-11-13", null,
                List.of(
                        new AdsDailyReportService.DailyRow("brand", "2026-11-12", "search", 100L, 9L, new BigDecimal("4.5")),
                        new AdsDailyReportService.DailyRow("brand", "2026-11-13", "SEARCH_PARTNERS", 20L, 2L, new BigDecimal("1")),
                        new AdsDailyReportService.DailyRow("brand", "2025-01-01", "SEARCH", 1L, 1L, BigDecimal.ONE),
                        new AdsDailyReportService.DailyRow("brand", "bad-date", "SEARCH", 1L, 1L, BigDecimal.ONE)),
                null);

        AdsDailyReportService.IngestResult result = service.ingest("key", request);

        assertEquals(2, result.dailyRows());
        ArgumentCaptor<Iterable<AdsDailyStat>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(dailyStatRepository).saveAll(captor.capture());
        List<AdsDailyStat> saved = new ArrayList<>();
        captor.getValue().forEach(saved::add);
        assertSame(existing, saved.get(0));
        assertEquals(9L, existing.getClicks());
        assertEquals("CNY", existing.getCurrency());
        assertEquals("SEARCH_PARTNERS", saved.get(1).getNetwork());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergesDuplicateSearchTermsAndReplacesTodaysSnapshot() {
        AdsDailyReportService.DailyReportRequest request = new AdsDailyReportService.DailyReportRequest(
                "USD", "2026-11-13", null, null,
                List.of(
                        new AdsDailyReportService.SearchTermRow("brand", "Brand  Login", 10L, 2L, new BigDecimal("0.4")),
                        new AdsDailyReportService.SearchTermRow("brand", "brand login", 5L, 1L, new BigDecimal("0.2")),
                        new AdsDailyReportService.SearchTermRow("brand", "brand sale", 8L, 3L, new BigDecimal("0.6"))));

        AdsDailyReportService.IngestResult result = service.ingest("key", request);

        assertEquals(2, result.searchTerms());
        verify(searchTermRepository).deleteSnapshot("13800000000", LocalDate.of(2026, 11, 13), Set.of("brand"));
        ArgumentCaptor<Iterable<AdsSearchTermStat>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(searchTermRepository).saveAll(captor.capture());
        AdsSearchTermStat login = captor.getValue().iterator().next();
        assertEquals("brand login", login.getSearchTerm());
        assertEquals(3L, login.getClicks());
        assertEquals(0, new BigDecimal("0.6").compareTo(login.getCost()));
    }
}
