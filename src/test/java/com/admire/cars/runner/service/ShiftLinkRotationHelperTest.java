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

    private static ShiftLink link(Long id, Long clickBase, Long displayTimes) {
        ShiftLink link = new ShiftLink();
        link.setId(id);
        link.setClickBase(clickBase);
        link.setDisplayTimes(displayTimes);
        return link;
    }
}
