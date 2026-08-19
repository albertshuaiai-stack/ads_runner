package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.ExchangeRate;
import com.admire.cars.runner.service.CurrencyExchangeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/currency-exchange-rate", "/currency-exchange-rate"})
public class CurrencyExchangeRateController {

    private final CurrencyExchangeService currencyExchangeService;

    public CurrencyExchangeRateController(CurrencyExchangeService currencyExchangeService) {
        this.currencyExchangeService = currencyExchangeService;
    }

    @GetMapping
    public ResponseEntity<ExchangeRate> getLatestExchangeRate() {
        try {
            return ResponseEntity.ok(currencyExchangeService.getLatestExchangeRate());
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
