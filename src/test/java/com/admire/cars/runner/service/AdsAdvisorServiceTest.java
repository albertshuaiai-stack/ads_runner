package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignPlan;
import com.admire.cars.runner.entity.AdsCampaignSnapshot;
import com.admire.cars.runner.entity.AdsDailyStat;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.entity.AdsSearchTermStat;
import com.admire.cars.runner.repository.AdsCampaignPlanRepository;
import com.admire.cars.runner.repository.AdsCampaignSnapshotRepository;
import com.admire.cars.runner.repository.AdsDailyStatRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import com.admire.cars.runner.repository.AdsSearchTermStatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdsAdvisorServiceTest {

    private static final String OWNER = "18565545084";

    @Mock
    private AdsCampaignPlanRepository planRepository;
    @Mock
    private AdsDailyStatRepository dailyStatRepository;
    @Mock
    private AdsCampaignSnapshotRepository snapshotRepository;
    @Mock
    private AdsSearchTermStatRepository searchTermRepository;
    @Mock
    private AdsNormalInfoRepository normalInfoRepository;
    @Mock
    private AdsNormalPostBackRepository postBackRepository;
    @Mock
    private PostBackAttributionService attributionService;

    private AdsNormalInfo task(long id, String name, String status, Long clicks, String cost, String currency) {
        AdsNormalInfo task = new AdsNormalInfo();
        task.setId(id);
        task.setAdsOwner(OWNER);
        task.setCampainName(name);
        task.setCampainCountry("US");
        task.setStatus(status);
        task.setLastReportClicks(clicks);
        task.setLastReportCost(cost == null ? null : new BigDecimal(cost));
        task.setCostCurrency(currency);
        return task;
    }

    @Test
    @SuppressWarnings("unchecked")
    void reviewHandlesTasksWithAndWithoutPlansOrdersAndDailyData() {
        LocalDate today = LocalDate.now();
        when(attributionService.resolveOwnerForRead(isNull(), isNull())).thenReturn(null);
        lenient().when(attributionService.toUsd(eq("CNY"), any())).thenReturn(new BigDecimal("0.14"));
        lenient().when(attributionService.toUsd(eq("USD"), any())).thenReturn(BigDecimal.ONE);
        lenient().when(attributionService.toUsd(eq("JPY"), any())).thenReturn(null);

        when(normalInfoRepository.findAllByOrderByIdDesc()).thenReturn(List.of(
                task(1, "noplan", "RUNNING", 50L, "20", "CNY"),
                task(2, "teepublic", "RUNNING", 84L, "128.18", "CNY"),
                task(3, "planned", "RUNNING", null, null, null),
                task(4, "yen", "RUNNING", 10L, "500", "JPY")));

        AdsCampaignPlan plan = new AdsCampaignPlan();
        plan.setAdsOwner(OWNER);
        plan.setCampaignName("planned");
        plan.setCommissionValue(new BigDecimal("5"));
        when(planRepository.findByOwner(null)).thenReturn(List.of(plan));

        AdsCampaignSnapshot snapshot = new AdsCampaignSnapshot();
        snapshot.setAdsOwner(OWNER);
        snapshot.setCampaignName("teepublic");
        snapshot.setCurrency("CNY");
        snapshot.setSearchPartners(true);
        snapshot.setBiddingStrategy("TARGET_SPEND");
        when(snapshotRepository.findByOwner(null)).thenReturn(List.of(snapshot));

        AdsDailyStat search = new AdsDailyStat();
        search.setAdsOwner(OWNER);
        search.setCampaignName("teepublic");
        search.setStatDate(today);
        search.setNetwork("SEARCH");
        search.setClicks(5L);
        search.setImpressions(100L);
        search.setCost(new BigDecimal("10"));
        search.setCurrency("CNY");
        AdsDailyStat partners = new AdsDailyStat();
        partners.setAdsOwner(OWNER);
        partners.setCampaignName("teepublic");
        partners.setStatDate(today.minusDays(1));
        partners.setNetwork("SEARCH_PARTNERS");
        partners.setClicks(3L);
        partners.setImpressions(0L);
        partners.setCost(null);
        partners.setCurrency("CNY");
        when(dailyStatRepository.findSince(isNull(), any())).thenReturn(List.of(search, partners));

        AdsSearchTermStat term = new AdsSearchTermStat();
        term.setAdsOwner(OWNER);
        term.setCampaignName("teepublic");
        term.setSnapshotDate(today);
        term.setSearchTerm("teepublic login");
        term.setClicks(2L);
        term.setImpressions(9L);
        term.setCost(null);
        when(searchTermRepository.findSince(isNull(), any())).thenReturn(List.of(term));

        AdsNormalPostBack order = new AdsNormalPostBack();
        order.setNormalAdsId(2L);
        order.setStatus("Pending");
        order.setOrderTime(String.valueOf(System.currentTimeMillis() / 1000));
        order.setUserCommissionAmount(new BigDecimal("0.76"));
        AdsNormalPostBack noTime = new AdsNormalPostBack();
        noTime.setNormalAdsId(2L);
        noTime.setStatus(null);
        noTime.setOrderTime("0");
        noTime.setUserCommissionAmount(null);
        when(postBackRepository.findAttributedByOwner(null)).thenReturn(List.of(order, noTime));

        AdsAdvisorService service = new AdsAdvisorService(planRepository, dailyStatRepository, snapshotRepository,
                searchTermRepository, normalInfoRepository, postBackRepository, attributionService, new AdsPlanEvaluator());

        Map<String, Object> review = service.review(null, null);

        List<Map<String, Object>> tasks = (List<Map<String, Object>>) review.get("tasks");
        assertEquals(4, tasks.size());
        Map<String, Object> byName = Map.of();
        for (Map<String, Object> row : tasks) {
            if ("noplan".equals(row.get("campaignName"))) {
                assertEquals(AdsReviewRules.WATCH, row.get("decision"));
            }
            if ("planned".equals(row.get("campaignName"))) {
                assertEquals(AdsReviewRules.NO_DATA, row.get("decision"));
            }
            if ("yen".equals(row.get("campaignName"))) {
                assertEquals(AdsReviewRules.NO_DATA, row.get("decision"));
            }
            if ("teepublic".equals(row.get("campaignName"))) {
                byName = row;
            }
        }
        List<String> actions = (List<String>) byName.get("actions");
        assertTrue(actions.stream().anyMatch(a -> a.contains("Search Partners")));
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) byName.get("negativeCandidates");
        assertEquals("teepublic login", candidates.get(0).get("term"));
        assertFalse(String.valueOf(review.get("markdown")).isBlank());
    }
}
