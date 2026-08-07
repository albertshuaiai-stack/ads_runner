package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateTest;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AffiliateTestRepository;
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
class AffiliateTestServiceTest {

    @Mock
    private AffiliateTestRepository resultRepository;

    @Mock
    private UserRepository userRepository;

    private AffiliateTestService service;

    @BeforeEach
    void setUp() {
        service = new AffiliateTestService(resultRepository, userRepository);
    }

    @Test
    void getById_and_search() {
        User user = new User();
        user.setId(1L);
        user.setUserPhoneNumber("13800000000");
        user.setUserRole("user");

        AffiliateTest result = new AffiliateTest();
        result.setId(1L);
        result.setAffiliateNetwork("BonusArrive");
        result.setRegion("US");
        result.setSiteName("Site A");
        result.setStatus("SUCCESS");
        result.setAdsOwner("13800000000");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(resultRepository.findById(1L)).thenReturn(Optional.of(result));
        when(resultRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(result)));

        AffiliateTest found = service.getById(1L, 1L);
        assertNotNull(found);
        assertEquals("BonusArrive", found.getAffiliateNetwork());

        assertEquals(1, service.search("13800000000", "BonusArrive", "US", "SUCCESS", 1L, PageRequest.of(0, 10)).getTotalElements());
    }
}
