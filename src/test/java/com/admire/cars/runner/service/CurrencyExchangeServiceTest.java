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

    @Test
    void toUsdUsingLatestRate_usesInverseWhenOnlyUsdToCnyExists() {
        ExchangeRate usdToCny = new ExchangeRate();
        usdToCny.setFromCurrency("USD");
        usdToCny.setToCurrency("CNY");
        usdToCny.setRate(new BigDecimal("7.25000000"));

        CurrencyExchangeService service = new CurrencyExchangeService(exchangeRateRepository);
        when(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDescCreateDateDescIdDesc("CNY", "USD"))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDescCreateDateDescIdDesc("USD", "CNY"))
                .thenReturn(Optional.of(usdToCny));

        BigDecimal result = service.toUsdUsingLatestRate(new BigDecimal("72.5"), "CNY");

        assertEquals(new BigDecimal("10.0000"), result);
    }

    @Test
    void toUsd_usesInverseRateOnOrBeforeDateWhenDirectPairMissing() {
        ExchangeRate usdToCny = new ExchangeRate();
        usdToCny.setFromCurrency("USD");
        usdToCny.setToCurrency("CNY");
        usdToCny.setRate(new BigDecimal("7.30000000"));
        usdToCny.setEffectiveDate(LocalDate.of(2026, 8, 19));

        CurrencyExchangeService service = new CurrencyExchangeService(exchangeRateRepository);
        when(exchangeRateRepository.findLatestRate("CNY", "USD", LocalDate.of(2026, 8, 20)))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.findLatestRate("USD", "CNY", LocalDate.of(2026, 8, 20)))
                .thenReturn(Optional.of(usdToCny));

        BigDecimal result = service.toUsd(new BigDecimal("73"), "CNY", LocalDate.of(2026, 8, 20));

        assertEquals(new BigDecimal("10.0000"), result);
    }
}
