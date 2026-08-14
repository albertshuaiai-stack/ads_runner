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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    }
}
