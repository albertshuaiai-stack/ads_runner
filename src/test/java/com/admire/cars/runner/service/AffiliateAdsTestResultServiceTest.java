package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsPlatform;
import com.admire.cars.runner.entity.AffiliateAdsTestResult;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AdsPlatformRepository;
import com.admire.cars.runner.repository.AffiliateAdsTestResultRepository;
import com.admire.cars.runner.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AffiliateAdsTestResultServiceTest {

    @Mock
    private AffiliateAdsTestResultRepository resultRepository;

    @Mock
    private AdsPlatformRepository adsPlatformRepository;

    @Mock
    private UserRepository userRepository;

    private AffiliateAdsTestResultService service;

    @BeforeEach
    void setUp() {
        service = new AffiliateAdsTestResultService(
                resultRepository,
                adsPlatformRepository,
                userRepository);
    }

    @Test
    void create_and_searchResult() {
        User user = new User();
        user.setId(1L);
        user.setUserPhoneNumber("13800000000");
        user.setUserRole("user");

        AdsPlatform platform = new AdsPlatform();
        platform.setId(10L);
        platform.setPlatformName("BonusArrive");

        AffiliateAdsTestResult result = new AffiliateAdsTestResult();
        result.setAffiliateNetwork("BonusArrive");
        result.setRegion("US");
        result.setSiteName("Site A");
        result.setStatus("SUCCESS");
        result.setAdsOwner("13800000000");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUserPhoneNumber("13800000000")).thenReturn(Optional.of(user));
        when(adsPlatformRepository.findByPlatformNameIgnoreCase("BonusArrive")).thenReturn(Optional.of(platform));
        when(resultRepository.save(any(AffiliateAdsTestResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AffiliateAdsTestResult created = service.create(result, 1L);
        assertNotNull(created);
        assertEquals("BonusArrive", created.getAffiliateNetwork());
        assertEquals("SUCCESS", created.getStatus());

        when(resultRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(created)));
        assertEquals(1, service.search("13800000000", "BonusArrive", "US", "SUCCESS", 1L, PageRequest.of(0, 10)).getTotalElements());
    }
}
