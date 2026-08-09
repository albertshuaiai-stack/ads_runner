package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ExchangeRate;
import com.admire.cars.runner.repository.ExchangeRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(readOnly = true)
public class CurrencyExchangeService {

    private static final String USD = "USD";

    private final ExchangeRateRepository exchangeRateRepository;

    // Cache rates per (fromCurrency, asOfDate) to avoid repeated DB calls in one request
    private final Map<String, BigDecimal> rateCache = new ConcurrentHashMap<>();

    public CurrencyExchangeService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * Converts the given amount to USD using the most recent rate on or before asOf.
     * If currency is already USD, returns the amount unchanged.
     * Throws if no rate is configured for the pair.
     */
    public BigDecimal toUsd(BigDecimal amount, String currency, LocalDate asOf) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (USD.equalsIgnoreCase(currency)) {
            return amount;
        }
        BigDecimal rate = resolveRate(currency, USD, asOf);
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveRate(String fromCurrency, String toCurrency, LocalDate asOf) {
        String cacheKey = fromCurrency + "_" + toCurrency + "_" + asOf;
        return rateCache.computeIfAbsent(cacheKey, k -> {
            ExchangeRate exchangeRate = exchangeRateRepository
                    .findLatestRate(fromCurrency, toCurrency, asOf)
                    .orElseThrow(() -> new IllegalStateException(
                            "No exchange rate configured for " + fromCurrency + " → " + toCurrency
                            + " on or before " + asOf));
            return exchangeRate.getRate();
        });
    }
}
