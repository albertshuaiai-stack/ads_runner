package com.admire.cars.runner.service;

import com.admire.cars.runner.config.ShiftLinkClickConfig;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

        service = new TrackReportService(userService, linkRepository, helper,
                mock(NormalShiftLinkQuotaService.class), mock(AdsNormalInfoRepository.class));
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
                List.of(new TrackReportService.CampaignClicks(CAMPAIGN, clicks, null, null)));
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
}
