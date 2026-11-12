package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsClickTrack;
import com.admire.cars.runner.entity.AdsNormalInfo;
import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.repository.AdsClickTrackRepository;
import com.admire.cars.runner.repository.AdsNormalInfoRepository;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import com.admire.cars.runner.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    private PostBackAttributionService service;

    private static final String OWNER = "13800000000";
    private static final String ORDER_NO = "order-1";
    private static final String CLICK_ID = "11111111-2222-3333-4444-555555555555";

    @BeforeEach
    void setUp() {
        service = new PostBackAttributionService(postBackRepository, clickTrackRepository,
                adsNormalInfoRepository, shiftLinkRepository, userRepository);
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
}
