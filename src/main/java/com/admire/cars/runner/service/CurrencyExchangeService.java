package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ExchangeRate;
import com.admire.cars.runner.repository.ExchangeRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
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
        String normalizedCurrency = normalizeCurrency(currency);
        if (USD.equalsIgnoreCase(normalizedCurrency)) {
            return amount;
        }
        BigDecimal rate = resolveRate(normalizedCurrency, USD, asOf);
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Converts the given amount to USD using the latest configured rate for the currency pair.
     */
    public BigDecimal toUsdUsingLatestRate(BigDecimal amount, String currency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        String normalizedCurrency = normalizeCurrency(currency);
        if (USD.equalsIgnoreCase(normalizedCurrency)) {
            return amount;
        }
        BigDecimal rate = resolveLatestRate(normalizedCurrency, USD);
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

    private BigDecimal resolveLatestRate(String fromCurrency, String toCurrency) {
        String cacheKey = fromCurrency + "_" + toCurrency + "_LATEST";
        return rateCache.computeIfAbsent(cacheKey, k -> {
            ExchangeRate exchangeRate = exchangeRateRepository
                    .findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDescCreateDateDescIdDesc(fromCurrency, toCurrency)
                    .orElseThrow(() -> new IllegalStateException(
                            "No latest exchange rate configured for " + fromCurrency + " → " + toCurrency));
            return exchangeRate.getRate();
        });
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) {
            return null;
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if ("$".equals(normalized)) {
            return USD;
        }
        if ("￥".equals(normalized) || "¥".equals(normalized)) {
            return "CNY";
        }
        return normalized;
    }

    public ExchangeRate getLatestExchangeRate() {
        return exchangeRateRepository.findTopByOrderByEffectiveDateDescCreateDateDescIdDesc()
                .orElseThrow(() -> new IllegalStateException("No exchange rate records found"));
    }
}
