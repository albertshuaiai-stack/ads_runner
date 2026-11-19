package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsNormalPostBackRepository;
import com.admire.cars.runner.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdsNormalPostBackServiceTest {

    @Mock
    private AdsNormalPostBackRepository adsNormalPostBackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    private AdsNormalPostBackService service;

    @BeforeEach
    void setUp() {
        service = new AdsNormalPostBackService(adsNormalPostBackRepository, userService, userRepository);
    }

    @Test
    void create_and_search() {
        User user = new User();
        user.setId(1L);
        user.setUserPhoneNumber("13800000000");
        user.setUserRole("user");

        AdsNormalPostBack postBack = new AdsNormalPostBack();
        postBack.setAffiliateSite("SiteA");
        postBack.setAdvertiserShopId("shop-1");
        postBack.setAdvertiserShopName("Shop One");
        postBack.setSignId("sign-1");
        postBack.setOrderNo("order-1");
        postBack.setOrderTime("2026-08-14 10:00:00");
        postBack.setOrderAmount(new BigDecimal("100.00"));
        postBack.setUserCommissionAmount(new BigDecimal("12.50"));
        postBack.setStatus("SUCCESS");
        postBack.setClickTime("2026-08-14 09:55:00");

        AdsNormalPostBack saved = new AdsNormalPostBack();
        saved.setId(1L);
        saved.setAdsOwner("13800000000");
        saved.setAffiliateSite("SiteA");
        saved.setAdvertiserShopId("shop-1");
        saved.setAdvertiserShopName("Shop One");
        saved.setSignId("sign-1");
        saved.setOrderNo("order-1");
        saved.setOrderTime(postBack.getOrderTime());
        saved.setOrderAmount(postBack.getOrderAmount());
        saved.setUserCommissionAmount(postBack.getUserCommissionAmount());
        saved.setStatus("SUCCESS");
        saved.setClickTime(postBack.getClickTime());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUserPhoneNumber("13800000000")).thenReturn(Optional.of(user));
        when(adsNormalPostBackRepository.save(any(AdsNormalPostBack.class))).thenReturn(saved);
        when(adsNormalPostBackRepository.findById(1L)).thenReturn(Optional.of(saved));
        when(adsNormalPostBackRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(saved)));
        when(userService.getEnabledUserByApiKey("api-key")).thenReturn(user);

        AdsNormalPostBack created = service.create(postBack, "api-key");
        assertNotNull(created);
        assertEquals("13800000000", created.getAdsOwner());

        AdsNormalPostBack found = service.getById(1L, 1L);
        assertEquals("order-1", found.getOrderNo());
        assertEquals(1, service.search(null, null, null, null, 1L, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, service.search("13800000000", null, null, null, null, PageRequest.of(0, 10)).getTotalElements());
        assertThrows(IllegalArgumentException.class,
                () -> service.search(null, null, null, null, null, PageRequest.of(0, 10)));
    }

    @Test
    void create_statusUpdateWithoutSubId_keepsStoredSubId() {
        User user = new User();
        user.setId(1L);
        user.setUserPhoneNumber("13800000000");
        user.setUserRole("user");

        AdsNormalPostBack existing = new AdsNormalPostBack();
        existing.setId(5L);
        existing.setAdsOwner("13800000000");
        existing.setOrderNo("order-1");
        existing.setStatus("pending");
        existing.setSubId("11111111-2222-3333-4444-555555555555");
        existing.setSubId2("11111111-2222-3333-4444-555555555555");
        existing.setClickTime("2026-11-12 10:00:00");

        AdsNormalPostBack incoming = new AdsNormalPostBack();
        incoming.setOrderNo("order-1");
        incoming.setStatus("approved");

        when(userService.getEnabledUserByApiKey("api-key")).thenReturn(user);
        when(userRepository.findByUserPhoneNumber("13800000000")).thenReturn(Optional.of(user));
        when(adsNormalPostBackRepository.findByAdsOwnerAndOrderNo("13800000000", "order-1"))
                .thenReturn(Optional.of(existing));
        when(adsNormalPostBackRepository.save(any(AdsNormalPostBack.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdsNormalPostBack updated = service.create(incoming, "api-key");

        assertEquals("approved", updated.getStatus());
        assertEquals("11111111-2222-3333-4444-555555555555", updated.getSubId());
        assertEquals("11111111-2222-3333-4444-555555555555", updated.getSubId2());
        assertEquals("2026-11-12 10:00:00", updated.getClickTime());
    }

    @Test
    void search_allows_admin_without_ads_owner_filter() {
        User admin = new User();
        admin.setId(1L);
        admin.setUserPhoneNumber("13800000000");
        admin.setUserRole("admin");

        AdsNormalPostBack saved = new AdsNormalPostBack();
        saved.setId(1L);
        saved.setAdsOwner("13800000000");

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adsNormalPostBackRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(saved)));

        assertEquals(1, service.search(null, null, null, null, 1L, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    @SuppressWarnings("unchecked")
    void offerSummary_groupsByShopIdThenName_andCountsStatuses() {
        User admin = new User();
        admin.setId(1L);
        admin.setUserPhoneNumber("13800000000");
        admin.setUserRole("admin");

        AdsNormalPostBack neom1 = order("133280", "NEOM Wellbeing", "Pending", "20.91", "0.59", "2026-11-19 08:18:19");
        AdsNormalPostBack neom2 = order("133280", "NEOM Wellbeing", "Rejected", "3.98", "0.11", "2026-11-18 07:00:00");
        neom2.setMatchStatus("MATCHED");
        AdsNormalPostBack sally1 = order(null, "Sally Beauty UK", "pending", "215.42", "0.95", "2026-11-14 08:53:18");
        AdsNormalPostBack sally2 = order(null, "sally beauty uk", "approved", "29.84", "0.13", "2026-11-15 09:51:35");
        AdsNormalPostBack sally3 = order(null, "Sally Beauty UK", "pending", "58.29", "0.25", "2026-11-16 10:16:44");

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adsNormalPostBackRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(neom1, sally1, neom2, sally2, sally3));

        List<Map<String, Object>> rows = service.offerSummary(null, null, null, 1L);

        assertEquals(2, rows.size());
        Map<String, Object> sally = rows.get(0);
        assertEquals("Sally Beauty UK", sally.get("advertiserShopName"));
        assertEquals(3L, sally.get("orderCount"));
        assertEquals(new BigDecimal("1.33"), sally.get("commission"));
        assertEquals(Map.of("pending", 2L, "approved", 1L), sally.get("orderStatusCounts"));
        assertEquals("2026-11-14 08:53:18", sally.get("firstOrderTime"));
        assertEquals("2026-11-16 10:16:44", sally.get("lastOrderTime"));

        Map<String, Object> neom = rows.get(1);
        assertEquals("133280", neom.get("advertiserShopId"));
        assertEquals(2L, neom.get("orderCount"));
        assertEquals(1L, neom.get("refundedOrderCount"));
        assertEquals(1L, neom.get("matchedOrderCount"));
        assertEquals(new BigDecimal("24.89"), neom.get("orderAmount"));
        assertEquals("BonusArrive", neom.get("affiliateSites"));
    }

    private static AdsNormalPostBack order(String shopId, String shopName, String status,
                                           String amount, String commission, String orderTime) {
        AdsNormalPostBack order = new AdsNormalPostBack();
        order.setAdsOwner("13800000000");
        order.setAffiliateSite("BonusArrive");
        order.setAdvertiserShopId(shopId);
        order.setAdvertiserShopName(shopName);
        order.setStatus(status);
        order.setOrderAmount(new BigDecimal(amount));
        order.setUserCommissionAmount(new BigDecimal(commission));
        order.setOrderTime(orderTime);
        return order;
    }
}
