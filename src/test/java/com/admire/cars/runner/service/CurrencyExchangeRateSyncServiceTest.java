package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ExchangeRate;
import com.admire.cars.runner.repository.ExchangeRateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyExchangeRateSyncServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private RestTemplate restTemplate;

    private CurrencyExchangeRateSyncService service;

    @BeforeEach
    void setUp() {
        service = new CurrencyExchangeRateSyncService(
                exchangeRateRepository,
                restTemplate,
                new ObjectMapper(),
                "https://api.frankfurter.app/latest?from=USD&to=CNY");
    }

    @Test
    void syncLatestRate_createsNewRow() {
        String responseBody = """
                {
                  "amount": 1.0,
                  "base": "USD",
                  "date": "2026-08-19",
                  "rates": {
                    "CNY": 7.2345
                  }
                }
                """;

        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(responseBody);
        when(exchangeRateRepository.findByFromCurrencyAndToCurrencyAndEffectiveDate("USD", "CNY", LocalDate.of(2026, 8, 19)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeRate saved = service.syncLatestRate();

        assertNotNull(saved);
        assertEquals("USD", saved.getFromCurrency());
        assertEquals("CNY", saved.getToCurrency());
        assertEquals(new BigDecimal("7.23450000"), saved.getRate());
        assertEquals(LocalDate.of(2026, 8, 19), saved.getEffectiveDate());
        assertEquals("Fetched from Frankfurter API", saved.getRemarks());
    }

    @Test
    void syncLatestRate_updatesExistingRow() {
        String responseBody = """
                {
                  "amount": 1.0,
                  "base": "USD",
                  "date": "2026-08-19",
                  "rates": {
                    "CNY": 7.3000
                  }
                }
                """;

        ExchangeRate existing = new ExchangeRate();
        existing.setId(10L);
        existing.setFromCurrency("USD");
        existing.setToCurrency("CNY");
        existing.setEffectiveDate(LocalDate.of(2026, 8, 19));
        existing.setRate(new BigDecimal("7.1000"));

        when(restTemplate.getForObject(anyString(), org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn(responseBody);
        when(exchangeRateRepository.findByFromCurrencyAndToCurrencyAndEffectiveDate("USD", "CNY", LocalDate.of(2026, 8, 19)))
                .thenReturn(Optional.of(existing));
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeRate saved = service.syncLatestRate();

        assertEquals(10L, saved.getId());
        assertEquals(new BigDecimal("7.30000000"), saved.getRate());
    }
}
