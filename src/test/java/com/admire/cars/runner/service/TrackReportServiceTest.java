package com.admire.cars.runner.service;

import com.admire.cars.runner.config.ShiftLinkClickConfig;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 最新优先换链规则 / newest-first rotation rule
 */
class TrackReportServiceTest {

    private static final String OWNER = "13800000000";
    private static final String API_KEY = "key";
    private static final String CAMPAIGN = "sharkninja";

    private ShiftLinkRepository linkRepository;
    private ShiftLinkClickConfig clickConfig;
    private ShiftLinkRotationHelper helper;
    private TrackReportService service;
    private NormalShiftLinkQuotaService quotaService;
    private AdsNormalInfoRepository normalInfoRepository;
    private AdsCampaignCostService costService;
    private final List<ShiftLink> normalLinks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        linkRepository = mock(ShiftLinkRepository.class);
        clickConfig = new ShiftLinkClickConfig();
        ReflectionTestUtils.setField(clickConfig, "normalCapacity", 1L);
        ReflectionTestUtils.setField(clickConfig, "matrixCapacity", 10L);
        helper = new ShiftLinkRotationHelper(linkRepository, clickConfig);

        UserService userService = mock(UserService.class);
        User user = new User();
        user.setUserPhoneNumber(OWNER);
        when(userService.getEnabledUserByApiKey(API_KEY)).thenReturn(user);
        when(linkRepository.findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(OWNER, CAMPAIGN, "Normal"))
                .thenReturn(normalLinks);
        when(linkRepository.findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(OWNER, CAMPAIGN, "Matrix"))
                .thenReturn(List.of());

        quotaService = mock(NormalShiftLinkQuotaService.class);
        normalInfoRepository = mock(AdsNormalInfoRepository.class);
        costService = mock(AdsCampaignCostService.class);
        service = new TrackReportService(userService, linkRepository, helper,
                quotaService, normalInfoRepository, costService);
    }

    private ShiftLink link(long id, Long clickBase, long displayTimes) {
        ShiftLink link = new ShiftLink();
        link.setId(id);
        link.setSeqNumber(id);
        link.setStatus("RUNNING");
        link.setClickBase(clickBase);
        link.setDisplayTimes(displayTimes);
        normalLinks.add(link);
        return link;
    }

    private void report(long clicks) {
        service.applyCampaignClicks(API_KEY,
                List.of(new TrackReportService.CampaignClicks(CAMPAIGN, clicks, null, null, null, null)));
    }

    @Test
    void firstReportActivatesNewestUnusedLink() {
        ShiftLink oldest = link(1, null, 0);
        ShiftLink newest = link(3, null, 0);
        ShiftLink middle = link(2, null, 0);

        report(100);

        assertEquals(100L, newest.getClickBase());
        assertNull(oldest.getClickBase());
        assertNull(middle.getClickBase());
    }

    @Test
    void usedUpLinkSwitchesToNewestUnusedAndSkipsOlderOnes() {
        ShiftLink active = link(5, 100L, 0);
        ShiftLink olderUnused = link(3, null, 0);
        ShiftLink newerUnused = link(8, null, 0);
        ShiftLink newestUnused = link(9, null, 0);

        report(101);

        assertEquals(1L, active.getDisplayTimes());
        assertEquals(101L, newestUnused.getClickBase());
        assertNull(olderUnused.getClickBase());
        assertNull(newerUnused.getClickBase());
        assertEquals(newestUnused, helper.selectForTemplate(normalLinks, 1L).orElseThrow());
    }

    @Test
    void noNewClicksKeepsActiveLink() {
        ShiftLink active = link(5, 100L, 0);
        link(9, null, 0);

        report(100);

        assertEquals(0L, active.getDisplayTimes());
        verify(linkRepository, never()).save(any());
        assertEquals(active, helper.selectForTemplate(normalLinks, 1L).orElseThrow());
    }

    @Test
    void usedUpWithoutUnusedFallsBackUntilNewLinkArrives() {
        ShiftLink active = link(5, 100L, 0);

        report(101);
        assertEquals(1L, active.getDisplayTimes());
        assertTrue(helper.selectForTemplate(normalLinks, 1L).isEmpty());

        // 已用完的链点击数冻结, 不再累加 / a used-up link keeps its frozen count
        report(105);
        assertEquals(1L, active.getDisplayTimes());

        ShiftLink fresh = link(6, null, 0);
        report(106);
        assertEquals(106L, fresh.getClickBase());
        assertEquals(fresh, helper.selectForTemplate(normalLinks, 1L).orElseThrow());
    }

    @Test
    void raisedThresholdNeverReturnsToRetiredLink() {
        ShiftLink retired = link(5, 100L, 1);
        ShiftLink active = link(9, 101L, 0);

        // 阈值从 1 调到 10 后, 旧链 displayTimes=1 < 10, 仍不能回头用 / retired link stays retired after raising the threshold
        assertEquals(active, helper.selectForTemplate(normalLinks, 10L).orElseThrow());
        assertEquals(active, helper.current(normalLinks).orElseThrow());
        assertEquals(100L, retired.getClickBase());
    }

    // 暂停的同名旧广告只记花费, 不能用它的点击数换链 / a paused campaign only updates the ledger, never rotation
    @Test
    void pausedCampaignOnlyRecordsCost() {
        ShiftLink active = link(5, 100L, 0);
        link(9, null, 0);

        service.applyCampaignClicks(API_KEY, List.of(new TrackReportService.CampaignClicks(
                CAMPAIGN, 500L, new BigDecimal("88.5"), "CNY", "111", "PAUSED")));

        assertEquals(0L, active.getDisplayTimes());
        verify(linkRepository, never()).save(any());
        verify(quotaService, never()).recordClicks(any(), any(), anyLong());
        verify(costService).record(OWNER, "111", CAMPAIGN, "PAUSED", new BigDecimal("88.5"), "CNY", null);
    }

    @Test
    void enabledCampaignRecordsCostAgainstNewestTask() {
        AdsNormalInfo task = new AdsNormalInfo();
        task.setId(9L);
        when(normalInfoRepository.findByAdsOwnerAndCampainNameOrderByIdDesc(OWNER, CAMPAIGN)).thenReturn(List.of(task));

        service.applyCampaignClicks(API_KEY, List.of(new TrackReportService.CampaignClicks(
                CAMPAIGN, 10L, new BigDecimal("12.34"), "cny", "222", "ENABLED")));

        verify(costService).record(OWNER, "222", CAMPAIGN, "ENABLED", new BigDecimal("12.34"), "cny", 9L);
        assertEquals(new BigDecimal("12.34"), task.getLastReportCost());
        assertEquals("CNY", task.getCostCurrency());
    }

    // 台账写入失败不能影响换链 / a ledger failure must not break rotation
    @Test
    void ledgerFailureDoesNotBreakRotation() {
        ShiftLink newest = link(3, null, 0);
        doThrow(new IllegalStateException("duplicate key")).when(costService)
                .record(any(), any(), any(), any(), any(), any(), any());

        service.applyCampaignClicks(API_KEY, List.of(new TrackReportService.CampaignClicks(
                CAMPAIGN, 100L, BigDecimal.ONE, "CNY", "333", "ENABLED")));

        assertEquals(100L, newest.getClickBase());
    }
}
