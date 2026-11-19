package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsCampaignCost;
import com.admire.cars.runner.entity.AdsClickTrack;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.entity.ExchangeRate;
import com.admire.cars.runner.repository.AdsCampaignCostRepository;
import com.admire.cars.runner.repository.AdsClickTrackRepository;
import com.admire.cars.runner.repository.AdsDailyStatRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import com.admire.cars.runner.repository.ExchangeRateRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostBackAttributionServiceTest {

    @Mock
    private AdsNormalPostBackRepository postBackRepository;
    @Mock
    private AdsClickTrackRepository clickTrackRepository;
    @Mock
    private AdsNormalInfoRepository adsNormalInfoRepository;
    @Mock
    private ShiftLinkRepository shiftLinkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExchangeRateRepository exchangeRateRepository;
    @Mock
    private AdsDailyStatRepository adsDailyStatRepository;
    @Mock
    private AdsCampaignCostRepository campaignCostRepository;

    private PostBackAttributionService service;

    private static final String OWNER = "13800000000";
    private static final String ORDER_NO = "order-1";
    private static final String CLICK_ID = "11111111-2222-3333-4444-555555555555";

    @BeforeEach
    void setUp() {
        service = new PostBackAttributionService(postBackRepository, clickTrackRepository,
                adsNormalInfoRepository, shiftLinkRepository, userRepository, exchangeRateRepository,
                adsDailyStatRepository, campaignCostRepository);
    }

    private AdsNormalPostBack postBack(String subId) {
        AdsNormalPostBack postBack = new AdsNormalPostBack();
        postBack.setAdsOwner(OWNER);
        postBack.setOrderNo(ORDER_NO);
        postBack.setSubId(subId);
        return postBack;
    }

    private void mockFound(AdsNormalPostBack postBack) {
        when(postBackRepository.findByAdsOwnerAndOrderNo(OWNER, ORDER_NO)).thenReturn(Optional.of(postBack));
    }

    @Test
    void attribute_exactSubId_matchesClickIdAndNormalTask() {
        AdsNormalPostBack postBack = postBack(CLICK_ID);
        mockFound(postBack);
        AdsClickTrack track = new AdsClickTrack();
        track.setClickId(CLICK_ID);
        track.setNormalAdsId(12L);
        track.setShiftLinkId(34L);
        when(clickTrackRepository.findByClickId(CLICK_ID)).thenReturn(Optional.of(track));

        service.attribute(OWNER, ORDER_NO);

        assertEquals("MATCHED", postBack.getMatchStatus());
        assertEquals("EXACT_SUB_ID", postBack.getMatchRule());
        assertEquals(CLICK_ID, postBack.getClickId());
        assertEquals(12L, postBack.getNormalAdsId());
        assertEquals(34L, postBack.getShiftLinkId());
    }

    @Test
    void attribute_uuidInsideValue_stillMatches() {
        AdsNormalPostBack postBack = postBack("prefix_" + CLICK_ID + "_suffix");
        mockFound(postBack);
        AdsClickTrack track = new AdsClickTrack();
        track.setClickId(CLICK_ID);
        track.setNormalAdsId(9L);
        when(clickTrackRepository.findByClickId("prefix_" + CLICK_ID + "_suffix")).thenReturn(Optional.empty());
        when(clickTrackRepository.findByClickId(CLICK_ID)).thenReturn(Optional.of(track));

        service.attribute(OWNER, ORDER_NO);

        assertEquals("MATCHED", postBack.getMatchStatus());
        assertEquals("UUID_IN_VALUE", postBack.getMatchRule());
        assertEquals(9L, postBack.getNormalAdsId());
    }

    @Test
    void attribute_taskMarker_matchesNormalTaskOnly() {
        AdsNormalPostBack postBack = postBack("n12");
        mockFound(postBack);
        AdsNormalInfo task = new AdsNormalInfo();
        task.setId(12L);
        task.setAdsOwner(OWNER);
        when(clickTrackRepository.findByClickId(anyString())).thenReturn(Optional.empty());
        when(adsNormalInfoRepository.findById(12L)).thenReturn(Optional.of(task));

        service.attribute(OWNER, ORDER_NO);

        assertEquals("PARTIAL", postBack.getMatchStatus());
        assertEquals("NORMAL_ADS_ID", postBack.getMatchRule());
        assertEquals(12L, postBack.getNormalAdsId());
        assertNull(postBack.getClickId());
    }

    @Test
    void attribute_advertiserShopId_fallsBackToNormalTask() {
        AdsNormalPostBack postBack = postBack("platform-click-123");
        postBack.setAdvertiserShopId("319");
        mockFound(postBack);
        AdsNormalInfo task = new AdsNormalInfo();
        task.setId(7L);
        task.setCampainName("tequbic");
        task.setAffiliteUrl("https://www.bonusarrive.com/clink?a=319&c=429&subid={subid}");
        when(clickTrackRepository.findByClickId(anyString())).thenReturn(Optional.empty());
        when(adsNormalInfoRepository.findByAdsOwnerOrderByIdDesc(OWNER)).thenReturn(List.of(task));

        service.attribute(OWNER, ORDER_NO);

        assertEquals("PARTIAL", postBack.getMatchStatus());
        assertEquals("ADVERTISER_SHOP_ID", postBack.getMatchRule());
        assertEquals(7L, postBack.getNormalAdsId());
        assertNull(postBack.getClickId());
    }

    @Test
    void attribute_campaignName_fallsBackWhenShopIdMissing() {
        AdsNormalPostBack postBack = postBack("platform-click-456");
        postBack.setAdvertiserShopName("earlybeauty");
        mockFound(postBack);
        AdsNormalInfo task = new AdsNormalInfo();
        task.setId(21L);
        task.setCampainName("earlybeauty");
        task.setAffiliteUrl("https://www.bonusarrive.com/clink?a=48445&c=429&subid={subid}");
        when(clickTrackRepository.findByClickId(anyString())).thenReturn(Optional.empty());
        when(adsNormalInfoRepository.findByAdsOwnerOrderByIdDesc(OWNER)).thenReturn(List.of(task));

        service.attribute(OWNER, ORDER_NO);

        assertEquals("PARTIAL", postBack.getMatchStatus());
        assertEquals("ADVERTISER_SHOP_ID", postBack.getMatchRule());
        assertEquals(21L, postBack.getNormalAdsId());
    }

    @Test
    void attribute_alreadyMatched_isNotRecomputed() {
        AdsNormalPostBack postBack = postBack(null);
        postBack.setMatchStatus("MATCHED");
        postBack.setMatchRule("MANUAL_BIND");
        postBack.setClickId(CLICK_ID);
        postBack.setNormalAdsId(12L);
        mockFound(postBack);

        service.attribute(OWNER, ORDER_NO);

        assertEquals("MATCHED", postBack.getMatchStatus());
        assertEquals("MANUAL_BIND", postBack.getMatchRule());
        assertEquals(CLICK_ID, postBack.getClickId());
        assertEquals(12L, postBack.getNormalAdsId());
    }

    @Test
    void attribute_noCandidate_keepsUnmatched() {
        AdsNormalPostBack postBack = postBack("platform-click-9988");
        mockFound(postBack);
        when(clickTrackRepository.findByClickId(anyString())).thenReturn(Optional.empty());
        when(adsNormalInfoRepository.findByAdsOwnerOrderByIdDesc(OWNER)).thenReturn(List.of());

        service.attribute(OWNER, ORDER_NO);

        assertEquals("UNMATCHED", postBack.getMatchStatus());
        assertEquals("NONE", postBack.getMatchRule());
        assertNull(postBack.getNormalAdsId());
    }

    private static AdsCampaignCost ledgerRow(String key, String name, String cost, Long normalAdsId) {
        AdsCampaignCost row = new AdsCampaignCost();
        row.setAdsOwner(OWNER);
        row.setCampaignKey(key);
        row.setCampaignName(name);
        row.setLifetimeCost(new BigDecimal(cost));
        row.setCostCurrency("CNY");
        row.setNormalAdsId(normalAdsId);
        return row;
    }

    // 删除任务 / 删除重建广告后, 历史投入仍计入统计 / history survives task deletion and campaign recreation
    @Test
    @SuppressWarnings("unchecked")
    void stats_keepsAdCostOfDeletedTasksAndRecreatedCampaigns() {
        AdsNormalInfo task = new AdsNormalInfo();
        task.setId(11L);
        task.setAdsOwner(OWNER);
        task.setCampainName("momcozy");
        // 任务字段只剩新 campaign 的花费, 应以台账为准 / the task field only holds the new campaign's cost
        task.setLastReportCost(new BigDecimal("20"));
        task.setCostCurrency("CNY");
        when(adsNormalInfoRepository.findByAdsOwnerOrderByIdDesc(OWNER)).thenReturn(List.of(task));
        when(campaignCostRepository.findByAdsOwner(OWNER)).thenReturn(List.of(
                ledgerRow("ID:1", "momcozy", "100", 11L),
                ledgerRow("ID:2", "Momcozy", "20", 11L),
                ledgerRow("NAME:protonvpn", "protonvpn", "30", 25L),
                ledgerRow("ID:9", "matrixonly", "500", null)));
        ExchangeRate rate = new ExchangeRate();
        rate.setRate(new BigDecimal("0.14"));
        when(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDescCreateDateDescIdDesc("CNY", "USD"))
                .thenReturn(Optional.of(rate));

        Map<String, Object> stats = service.stats(OWNER, null);

        Map<String, Object> row = ((List<Map<String, Object>>) stats.get("tasks")).get(0);
        assertEquals(0, new BigDecimal("120").compareTo((BigDecimal) row.get("adCost")));
        assertEquals("CNY", row.get("costCurrency"));
        assertEquals(0, new BigDecimal("16.80").compareTo((BigDecimal) row.get("adCostUsd")));
        assertEquals(2, row.get("adCostCampaigns"));
        // 总花费 = momcozy 两个 campaign + 已删除任务 protonvpn, 从未绑定 Normal 任务的 matrixonly 不计
        // total = both momcozy campaigns + deleted protonvpn task; the never-bound matrix campaign is excluded
        assertEquals(0, new BigDecimal("21.00").compareTo((BigDecimal) stats.get("adCostUsd")));
        assertEquals(0, new BigDecimal("4.20").compareTo((BigDecimal) stats.get("deletedTaskAdCostUsd")));
        assertEquals(1, stats.get("deletedTaskCampaigns"));
        assertEquals(0, stats.get("unconvertedCostTasks"));
    }

    // 台账还没有记录时退回任务字段 / falls back to the task field before the ledger has a row
    @Test
    @SuppressWarnings("unchecked")
    void stats_fallsBackToTaskFieldWithoutLedger() {
        AdsNormalInfo task = new AdsNormalInfo();
        task.setId(5L);
        task.setAdsOwner(OWNER);
        task.setCampainName("xppen");
        task.setLastReportCost(new BigDecimal("10"));
        task.setCostCurrency("USD");
        when(adsNormalInfoRepository.findByAdsOwnerOrderByIdDesc(OWNER)).thenReturn(List.of(task));
        when(campaignCostRepository.findByAdsOwner(OWNER)).thenReturn(List.of());

        Map<String, Object> stats = service.stats(OWNER, null);

        Map<String, Object> row = ((List<Map<String, Object>>) stats.get("tasks")).get(0);
        assertEquals(0, new BigDecimal("10").compareTo((BigDecimal) row.get("adCostUsd")));
        assertEquals(0, new BigDecimal("10").compareTo((BigDecimal) stats.get("adCostUsd")));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) stats.get("deletedTaskAdCostUsd")));
    }
}
