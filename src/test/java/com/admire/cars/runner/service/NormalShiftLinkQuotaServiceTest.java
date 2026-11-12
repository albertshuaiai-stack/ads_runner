package com.admire.cars.runner.service;

import com.admire.cars.runner.config.NormalShiftLinkDailyConfig;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NormalShiftLinkQuotaServiceTest {

    private static final String OWNER = "13800000000";
    private static final String CAMPAIGN = "Normal-Campaign-A";

    private AdsNormalInfoRepository adsRepository;
    private ShiftLinkRepository linkRepository;
    private NormalShiftLinkDailyConfig config;
    private NormalShiftLinkQuotaService service;

    @BeforeEach
    void setUp() {
        adsRepository = mock(AdsNormalInfoRepository.class);
        linkRepository = mock(ShiftLinkRepository.class);
        config = new NormalShiftLinkDailyConfig();
        ReflectionTestUtils.setField(config, "enabled", true);
        ReflectionTestUtils.setField(config, "dailyBase", 20L);
        ReflectionTestUtils.setField(config, "clickMultiplier", 25L);
        service = new NormalShiftLinkQuotaService(adsRepository, linkRepository, config);
    }

    @Test
    void firstReportOfDayResetsAllowanceAndEarnsClickDelta() {
        AdsNormalInfo ads = ads(yesterday(), 100L, 500L);
        stubAds(ads);

        service.recordClicks(OWNER, CAMPAIGN, 105L);

        assertEquals(today(), ads.getQuotaDate());
        assertEquals(145L, ads.getDailyAllowance());
        assertEquals(105L, ads.getLastReportClicks());
        verify(adsRepository).save(ads);
    }

    @Test
    void sameDayReportAccumulatesClickDelta() {
        AdsNormalInfo ads = ads(today(), 105L, 145L);
        stubAds(ads);

        service.recordClicks(OWNER, CAMPAIGN, 108L);

        assertEquals(220L, ads.getDailyAllowance());
        assertEquals(108L, ads.getLastReportClicks());
    }

    @Test
    void firstEverReportGrantsBaseOnly() {
        AdsNormalInfo ads = ads(null, null, null);
        stubAds(ads);

        service.recordClicks(OWNER, CAMPAIGN, 100L);

        assertEquals(20L, ads.getDailyAllowance());
        assertEquals(100L, ads.getLastReportClicks());
        assertEquals(today(), ads.getQuotaDate());
    }

    @Test
    void clickDropNeverReducesAllowance() {
        AdsNormalInfo ads = ads(today(), 105L, 145L);
        stubAds(ads);

        service.recordClicks(OWNER, CAMPAIGN, 90L);

        assertEquals(145L, ads.getDailyAllowance());
        assertEquals(105L, ads.getLastReportClicks());
    }

    @Test
    void reportWithoutMatchingCampaignIsIgnored() {
        when(adsRepository.findByCampainNameAndAdsOwner(CAMPAIGN, OWNER)).thenReturn(Optional.empty());

        service.recordClicks(OWNER, CAMPAIGN, 100L);

        verify(adsRepository, never()).save(any());
    }

    @Test
    void allowsBaseLinksBeforeAnyReportToday() {
        AdsNormalInfo ads = ads(yesterday(), 100L, 999L);
        stubGeneratedToday(5L);

        assertTrue(service.allowGeneration(ads));
    }

    @Test
    void blocksWhenAllowanceUsedUp() {
        AdsNormalInfo ads = ads(today(), 100L, 20L);
        stubGeneratedToday(20L);

        assertFalse(service.allowGeneration(ads));
    }

    @Test
    void unusedAllowanceCarriesOverToLaterHours() {
        AdsNormalInfo ads = ads(today(), 100L, 145L);
        stubGeneratedToday(30L);

        assertTrue(service.allowGeneration(ads));
    }

    @Test
    void disabledSwitchRemovesCapAndSkipsTracking() {
        ReflectionTestUtils.setField(config, "enabled", false);

        assertTrue(service.allowGeneration(ads(today(), 100L, 0L)));
        service.recordClicks(OWNER, CAMPAIGN, 100L);
        verifyNoInteractions(linkRepository);
        verifyNoInteractions(adsRepository);
    }

    private void stubGeneratedToday(long count) {
        when(linkRepository.countNormalCreatedSince(eq(OWNER), eq(CAMPAIGN), any(LocalDateTime.class)))
                .thenReturn(count);
    }

    private void stubAds(AdsNormalInfo ads) {
        when(adsRepository.findByCampainNameAndAdsOwner(CAMPAIGN, OWNER)).thenReturn(Optional.of(ads));
    }

    private static AdsNormalInfo ads(String quotaDate, Long lastClicks, Long allowance) {
        AdsNormalInfo ads = new AdsNormalInfo();
        ads.setId(7L);
        ads.setAdsOwner(OWNER);
        ads.setCampainName(CAMPAIGN);
        ads.setQuotaDate(quotaDate);
        ads.setLastReportClicks(lastClicks);
        ads.setDailyAllowance(allowance);
        return ads;
    }

    private static String today() {
        return LocalDate.now().toString();
    }

    private static String yesterday() {
        return LocalDate.now().minusDays(1).toString();
    }
}
