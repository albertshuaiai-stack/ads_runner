package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ExchangeRate;
import com.admire.cars.runner.repository.ExchangeRateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyExchangeServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Test
    void getLatestExchangeRate_returnsMostRecentRecord() {
        ExchangeRate latest = new ExchangeRate();
        latest.setId(2L);
        latest.setFromCurrency("USD");
        latest.setToCurrency("CNY");
        latest.setRate(new BigDecimal("7.30000000"));
        latest.setEffectiveDate(LocalDate.of(2026, 8, 19));
        latest.setCreateDate(LocalDateTime.of(2026, 8, 19, 4, 0));

        CurrencyExchangeService service = new CurrencyExchangeService(exchangeRateRepository);
        when(exchangeRateRepository.findTopByOrderByEffectiveDateDescCreateDateDescIdDesc())
                .thenReturn(Optional.of(latest));

        ExchangeRate result = service.getLatestExchangeRate();

        assertEquals(latest, result);
    }

    @Test
    void getLatestExchangeRate_throwsWhenEmpty() {
        CurrencyExchangeService service = new CurrencyExchangeService(exchangeRateRepository);
        when(exchangeRateRepository.findTopByOrderByEffectiveDateDescCreateDateDescIdDesc())
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, service::getLatestExchangeRate);
    }
}
