package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.ExchangeRate;
import com.admire.cars.runner.repository.ExchangeRateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class CurrencyExchangeRateSyncService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyExchangeRateSyncService.class);
    private static final String FROM_CURRENCY = "USD";
    private static final String TO_CURRENCY = "CNY";

    private final ExchangeRateRepository exchangeRateRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String exchangeRateApiUrl;

    public CurrencyExchangeRateSyncService(
            ExchangeRateRepository exchangeRateRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${exchange-rate.api.url:https://api.frankfurter.app/latest?from=USD&to=CNY}")
            String exchangeRateApiUrl) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.exchangeRateApiUrl = exchangeRateApiUrl;
    }

    @Transactional
    public ExchangeRate syncLatestRate() {
        CurrencyExchangeRateApiResponse apiResponse = fetchLatestRate();
        LocalDate effectiveDate = apiResponse.effectiveDate();
        BigDecimal rate = apiResponse.rate();

        ExchangeRate exchangeRate = exchangeRateRepository
                .findByFromCurrencyAndToCurrencyAndEffectiveDate(FROM_CURRENCY, TO_CURRENCY, effectiveDate)
                .orElseGet(ExchangeRate::new);
        exchangeRate.setFromCurrency(FROM_CURRENCY);
        exchangeRate.setToCurrency(TO_CURRENCY);
        exchangeRate.setRate(rate);
        exchangeRate.setEffectiveDate(effectiveDate);
        exchangeRate.setRemarks("Fetched from Frankfurter API");

        ExchangeRate saved = exchangeRateRepository.save(exchangeRate);
        log.info("EXCHANGE_RATE_SYNCED fromCurrency={} toCurrency={} effectiveDate={} rate={}",
                saved.getFromCurrency(), saved.getToCurrency(), saved.getEffectiveDate(), saved.getRate());
        return saved;
    }

    private CurrencyExchangeRateApiResponse fetchLatestRate() {
        String responseBody = restTemplate.getForObject(exchangeRateApiUrl, String.class);
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("Currency exchange API returned an empty response body");
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String baseCurrency = requireText(root, "base");
            if (!FROM_CURRENCY.equalsIgnoreCase(baseCurrency)) {
                throw new IllegalStateException("Currency exchange API returned unexpected base currency: " + baseCurrency);
            }

            String effectiveDateText = requireText(root, "date");
            JsonNode rateNode = root.path("rates").path(TO_CURRENCY);
            if (!rateNode.isNumber()) {
                throw new IllegalStateException("Currency exchange API response does not include rate for " + TO_CURRENCY);
            }

            return new CurrencyExchangeRateApiResponse(
                    LocalDate.parse(effectiveDateText),
                    rateNode.decimalValue().setScale(8, RoundingMode.HALF_UP));
        } catch (Exception ex) {
            throw new IllegalStateException("Currency exchange API response could not be parsed", ex);
        }
    }

    private String requireText(JsonNode root, String fieldName) {
        JsonNode value = root.path(fieldName);
        if (!value.isTextual() || !StringUtils.hasText(value.asText())) {
            throw new IllegalStateException("Currency exchange API response is missing field: " + fieldName);
        }
        return value.asText().trim();
    }

    private record CurrencyExchangeRateApiResponse(LocalDate effectiveDate, BigDecimal rate) {
    }
}
