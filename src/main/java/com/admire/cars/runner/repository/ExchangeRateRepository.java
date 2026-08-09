package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /**
     * Finds the most recent exchange rate for a currency pair on or before the given date.
     */
    @Query("SELECT e FROM ExchangeRate e " +
           "WHERE e.fromCurrency = :fromCurrency AND e.toCurrency = :toCurrency " +
           "AND e.effectiveDate <= :asOf " +
           "ORDER BY e.effectiveDate DESC")
    Optional<ExchangeRate> findLatestRate(
            @Param("fromCurrency") String fromCurrency,
            @Param("toCurrency") String toCurrency,
            @Param("asOf") LocalDate asOf);
}
