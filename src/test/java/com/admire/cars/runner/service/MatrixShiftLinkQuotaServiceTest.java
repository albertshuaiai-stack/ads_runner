package com.admire.cars.runner.service;

import com.admire.cars.runner.config.MatrixShiftLinkDailyConfig;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatrixShiftLinkQuotaServiceTest {

    private static final Long ADS_ID = 42L;
    private static final String OWNER = "13800000000";
    private static final String CAMPAIGN = "Matrix-Campaign-A";

    private ShiftLinkRepository repository;
    private MatrixShiftLinkDailyConfig config;
    private MatrixShiftLinkQuotaService service;

    @BeforeEach
    void setUp() {
        repository = mock(ShiftLinkRepository.class);
        config = new MatrixShiftLinkDailyConfig();
        ReflectionTestUtils.setField(config, "enabled", true);
        ReflectionTestUtils.setField(config, "dailyLimit", 24);
        service = new MatrixShiftLinkQuotaService(repository, config);
    }

    @Test
    void purgesStaleLinksThenAllowsGenerationWhenBelowLimit() {
        when(repository.deleteMatrixCreatedBefore(eq(OWNER), eq(CAMPAIGN), any(LocalDateTime.class))).thenReturn(7);
        when(repository.countMatrixCreatedSince(eq(OWNER), eq(CAMPAIGN), any(LocalDateTime.class))).thenReturn(5L);

        assertTrue(service.allowGeneration(ADS_ID, OWNER, CAMPAIGN));

        ArgumentCaptor<LocalDateTime> dayStart = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).deleteMatrixCreatedBefore(eq(OWNER), eq(CAMPAIGN), dayStart.capture());
        verify(repository).countMatrixCreatedSince(eq(OWNER), eq(CAMPAIGN), dayStart.capture());
        LocalDateTime expectedDayStart = LocalDate.now().atStartOfDay();
        assertEquals(expectedDayStart, dayStart.getAllValues().get(0));
        assertEquals(expectedDayStart, dayStart.getAllValues().get(1));
    }

    @Test
    void blocksGenerationWhenDailyLimitReached() {
        when(repository.countMatrixCreatedSince(eq(OWNER), eq(CAMPAIGN), any(LocalDateTime.class))).thenReturn(24L);

        assertFalse(service.allowGeneration(ADS_ID, OWNER, CAMPAIGN));
        verify(repository).deleteMatrixCreatedBefore(eq(OWNER), eq(CAMPAIGN), any(LocalDateTime.class));
    }

    @Test
    void zeroLimitPausesGeneration() {
        ReflectionTestUtils.setField(config, "dailyLimit", 0);

        assertFalse(service.allowGeneration(ADS_ID, OWNER, CAMPAIGN));
    }

    @Test
    void disabledSwitchSkipsBothPurgeAndCap() {
        ReflectionTestUtils.setField(config, "enabled", false);

        assertTrue(service.allowGeneration(ADS_ID, OWNER, CAMPAIGN));
        verifyNoInteractions(repository);
    }

    @Test
    void trimsOwnerToMatchPersistedLinks() {
        assertTrue(service.allowGeneration(ADS_ID, "  " + OWNER + "  ", CAMPAIGN));

        verify(repository).deleteMatrixCreatedBefore(eq(OWNER), eq(CAMPAIGN), any(LocalDateTime.class));
        verify(repository).countMatrixCreatedSince(eq(OWNER), eq(CAMPAIGN), any(LocalDateTime.class));
    }
}
