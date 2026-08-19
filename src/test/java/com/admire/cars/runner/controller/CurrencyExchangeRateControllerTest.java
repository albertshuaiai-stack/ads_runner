package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.ExchangeRate;
import com.admire.cars.runner.service.CurrencyExchangeService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class CurrencyExchangeRateControllerTest {

    @Test
    void getLatestExchangeRate_returnsOk() {
        CurrencyExchangeService service = Mockito.mock(CurrencyExchangeService.class);
        CurrencyExchangeRateController controller = new CurrencyExchangeRateController(service);

        ExchangeRate rate = new ExchangeRate();
        rate.setId(1L);
        rate.setFromCurrency("USD");
        rate.setToCurrency("CNY");
        rate.setRate(new BigDecimal("7.25000000"));
        rate.setEffectiveDate(LocalDate.of(2026, 8, 19));

        when(service.getLatestExchangeRate()).thenReturn(rate);

        var response = controller.getLatestExchangeRate();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("USD", response.getBody().getFromCurrency());
    }

    @Test
    void getLatestExchangeRate_returnsNotFoundWhenMissing() {
        CurrencyExchangeService service = Mockito.mock(CurrencyExchangeService.class);
        CurrencyExchangeRateController controller = new CurrencyExchangeRateController(service);
        when(service.getLatestExchangeRate()).thenThrow(new IllegalStateException("No exchange rate records found"));

        var response = controller.getLatestExchangeRate();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
