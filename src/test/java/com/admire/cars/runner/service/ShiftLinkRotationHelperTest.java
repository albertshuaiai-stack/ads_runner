package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.ShiftLink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShiftLinkRotationHelperTest {

    private final ShiftLinkRotationHelper helper = new ShiftLinkRotationHelper(null, null);

    @Test
    void availableCount_countsActiveLinkOnlyWhenNotUsedUp() {
        ShiftLink oldUsedUp = link(1L, 0L, 5L);
        ShiftLink active = link(2L, 5L, 3L);
        ShiftLink unusedA = link(3L, null, 0L);
        ShiftLink unusedB = link(4L, null, 0L);

        assertEquals(3L, helper.availableCount(List.of(oldUsedUp, active, unusedA, unusedB), 5L));

        active.setDisplayTimes(5L);
        assertEquals(2L, helper.availableCount(List.of(oldUsedUp, active, unusedA, unusedB), 5L));
        assertEquals(0L, helper.availableCount(List.of(), 5L));
    }

    @Test
    void rollCountDate_resetsCountersOnlyOnNewDay() {
        AdsNormalInfo ads = new AdsNormalInfo();
        ads.setCountDate("2026-11-19");
        ads.setSuccessCount(7L);
        ads.setFailedCount(2L);

        ads.rollCountDate("2026-11-19");
        assertEquals(7L, ads.getSuccessCount());

        ads.rollCountDate("2026-11-20");
        assertEquals(0L, ads.getSuccessCount());
        assertEquals(0L, ads.getFailedCount());
        assertEquals("2026-11-20", ads.getCountDate());
    }

    @Test
    void matrixRotationSkipsLinksWithSamePlatformAndEmail() {
        ShiftLink active = link(1L, 100L, 0L, "eBay", "a@example.com");
        ShiftLink sameSource = link(2L, null, 0L, "eBay", "a@example.com");
        ShiftLink samePlatform = link(3L, null, 0L, "eBay", "b@example.com");
        ShiftLink sameEmail = link(4L, null, 0L, "Amazon", "a@example.com");
        ShiftLink newerSameSource = link(5L, null, 0L, "ebay", " A@EXAMPLE.COM ");
        List<ShiftLink> links = List.of(active, sameSource, samePlatform, sameEmail, newerSameSource);

        assertEquals(sameEmail, helper.newestUnusedExcludingSameSource(links, active).orElseThrow());
        // 没有上一次落地页时不限制来源 / no previous link means no source restriction
        assertEquals(newerSameSource, helper.newestUnusedExcludingSameSource(links, null).orElseThrow());
    }

    @Test
    void matrixTemplateKeepsActiveWhenOnlySameSourceUnused() {
        ShiftLink active = link(1L, 100L, 5L, "eBay", "a@example.com");
        ShiftLink sameSource = link(2L, null, 0L, "eBay", "a@example.com");
        List<ShiftLink> links = List.of(active, sameSource);

        assertEquals(active, helper.selectForTemplate(links, 5L, true).orElseThrow());
        // Normal 不受平台/邮箱限制 / Normal rotation is not restricted by source
        assertEquals(sameSource, helper.selectForTemplate(links, 5L, false).orElseThrow());
    }

    private static ShiftLink link(Long id, Long clickBase, Long displayTimes) {
        return link(id, clickBase, displayTimes, null, null);
    }

    private static ShiftLink link(Long id, Long clickBase, Long displayTimes,
                                  String platformName, String userName) {
        ShiftLink link = new ShiftLink();
        link.setId(id);
        link.setClickBase(clickBase);
        link.setDisplayTimes(displayTimes);
        link.setPlatformName(platformName);
        link.setUserName(userName);
        return link;
    }
}
