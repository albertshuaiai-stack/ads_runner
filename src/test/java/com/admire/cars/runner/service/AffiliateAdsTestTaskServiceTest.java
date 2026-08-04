package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import com.admire.cars.runner.entity.AffiliateAdsTestTask;
import com.admire.cars.runner.entity.IpProxyInfo;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
import com.admire.cars.runner.repository.AffiliateAdsTestTaskRepository;
import com.admire.cars.runner.repository.IpProxyInfoRepository;
import com.admire.cars.runner.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AffiliateAdsTestTaskServiceTest {

    @Mock
    private AffiliateAdsTestTaskRepository taskRepository;

    @Mock
    private AffiliateAdsSyncConfigRepository configRepository;

    @Mock
    private IpProxyInfoRepository ipProxyInfoRepository;

    @Mock
    private UserRepository userRepository;

    private AffiliateAdsTestTaskService service;

    @BeforeEach
    void setUp() {
        service = new AffiliateAdsTestTaskService(
                taskRepository,
                configRepository,
                ipProxyInfoRepository,
                userRepository);
    }

    @Test
    void create_and_searchTask() {
        User user = new User();
        user.setId(1L);
        user.setUserPhoneNumber("13800000000");
        user.setUserRole("user");

        AffiliateAdsSyncConfig config = new AffiliateAdsSyncConfig();
        config.setId(10L);
        config.setAdsOwner("13800000000");

        IpProxyInfo proxyInfo = new IpProxyInfo();
        proxyInfo.setId(20L);
        proxyInfo.setAdsOwner("13800000000");

        AffiliateAdsTestTask task = new AffiliateAdsTestTask();
        task.setAffiliateAdsSyncConfigId(10L);
        task.setRegion("US");
        task.setIpProxyInfoId(20L);
        task.setAdsOwner("13800000000");
        task.setPreStartDate(LocalDateTime.now().minusSeconds(10));
        task.setPreEndDate(LocalDateTime.now());
        task.setPreDuration(10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUserPhoneNumber("13800000000")).thenReturn(Optional.of(user));
        when(configRepository.findById(10L)).thenReturn(Optional.of(config));
        when(ipProxyInfoRepository.findById(20L)).thenReturn(Optional.of(proxyInfo));
        when(taskRepository.save(any(AffiliateAdsTestTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AffiliateAdsTestTask created = service.create(task, 1L);
        assertNotNull(created);
        created.setId(1L);
        assertEquals("WAITING", created.getStatus());
        assertEquals("13800000000", created.getAdsOwner());
        assertEquals(10L, created.getPreDuration());

        when(taskRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(created)));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(created));
        assertEquals(1, service.search("13800000000", 10L, 1L, PageRequest.of(0, 10)).getTotalElements());

        AffiliateAdsTestTask inProgress = service.markInProgress(1L, 1L);
        assertEquals("IN_PROGRESS", inProgress.getStatus());
        assertNotNull(inProgress.getPreStartDate());
    }
}
