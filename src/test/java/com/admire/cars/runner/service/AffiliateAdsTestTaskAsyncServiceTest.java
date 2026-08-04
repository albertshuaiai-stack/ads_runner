package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AffiliateAdsSync;
import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import com.admire.cars.runner.entity.AffiliateAdsTestResult;
import com.admire.cars.runner.entity.AffiliateAdsTestTask;
import com.admire.cars.runner.repository.AffiliateAdsSyncConfigRepository;
import com.admire.cars.runner.repository.AffiliateAdsSyncRepository;
import com.admire.cars.runner.repository.AffiliateAdsTestTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AffiliateAdsTestTaskAsyncServiceTest {

    @Mock
    private AffiliateAdsTestTaskRepository taskRepository;

    @Mock
    private AffiliateAdsSyncConfigRepository configRepository;

    @Mock
    private AffiliateAdsSyncRepository syncRepository;

    @Mock
    private AffiliateAdsTestResultService resultService;

    private AffiliateAdsTestTaskAsyncService service;

    @BeforeEach
    void setUp() {
        service = new AffiliateAdsTestTaskAsyncService(
                taskRepository,
                configRepository,
                syncRepository,
                resultService);
    }

    @Test
    void testAdsAsync_insertsResultsAndCompletes() {
        AffiliateAdsTestTask task = new AffiliateAdsTestTask();
        task.setId(1L);
        task.setAffiliateAdsSyncConfigId(2L);
        task.setRegion("US");
        task.setAdsOwner("13800000000");
        task.setStatus("IN_PROGRESS");
        task.setPreStartDate(LocalDateTime.now().minusSeconds(2));

        AffiliateAdsSyncConfig config = new AffiliateAdsSyncConfig();
        config.setId(2L);
        config.setAffiliateNetwork("BonusArrive");
        config.setAdsOwner("13800000000");

        AffiliateAdsSync sync = new AffiliateAdsSync();
        sync.setId(10L);
        sync.setAffiliateNetwork("BonusArrive");
        sync.setRegion("US");
        sync.setSiteName("Site A");
        sync.setSiteUrl("https://site.example");
        sync.setTrackingUrl("https://track.example");
        sync.setStatus("ENABLED");
        sync.setAdsOwner("13800000000");

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(configRepository.findById(2L)).thenReturn(Optional.of(config));
        when(syncRepository.findAll(any(Specification.class))).thenReturn(List.of(sync));
        when(resultService.create(any(AffiliateAdsTestResult.class), eq(1L))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(AffiliateAdsTestTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.testAdsAsync(1L, 1L);

        ArgumentCaptor<AffiliateAdsTestResult> resultCaptor = ArgumentCaptor.forClass(AffiliateAdsTestResult.class);
        verify(resultService).create(resultCaptor.capture(), eq(1L));
        assertEquals("BonusArrive", resultCaptor.getValue().getAffiliateNetwork());
        assertEquals("US", resultCaptor.getValue().getRegion());
        assertEquals("SUCCESS", resultCaptor.getValue().getStatus());
        assertEquals("https://track.example", resultCaptor.getValue().getFinalUrl());

        assertEquals("COMPLETED", task.getStatus());
        assertNotNull(task.getPreEndDate());
        assertNotNull(task.getPreDuration());
    }
}
