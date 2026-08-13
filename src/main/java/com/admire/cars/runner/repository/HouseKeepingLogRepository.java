package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.HouseKeepingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HouseKeepingLogRepository extends JpaRepository<HouseKeepingLog, Long> {

    /**
     * Find all house keeping logs ordered by house keeping date in descending order
     *
     * @return list of house keeping logs sorted by date desc
     */
    @Query("SELECT h FROM HouseKeepingLog h ORDER BY h.houseKeepingDate DESC")
    List<HouseKeepingLog> findAllOrderByHouseKeepingDateDesc();

    /**
     * Find house keeping logs by date range ordered by house keeping date in descending order
     *
     * @param startDate the start date (inclusive)
     * @param endDate   the end date (inclusive)
     * @return list of house keeping logs within the date range sorted by date desc
     */
    @Query("SELECT h FROM HouseKeepingLog h WHERE h.houseKeepingDate BETWEEN :startDate AND :endDate ORDER BY h.houseKeepingDate DESC")
    List<HouseKeepingLog> findByDateRangeOrderByHouseKeepingDateDesc(LocalDate startDate, LocalDate endDate);
}
