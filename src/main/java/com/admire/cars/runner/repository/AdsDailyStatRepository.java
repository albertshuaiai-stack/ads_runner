package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsDailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AdsDailyStatRepository extends JpaRepository<AdsDailyStat, Long> {

    // owner 为 null 表示全部(admin) / null owner means all owners
    @Query("""
            select s from AdsDailyStat s
            where (:adsOwner is null or s.adsOwner = :adsOwner)
              and s.statDate >= :fromDate
            """)
    List<AdsDailyStat> findSince(@Param("adsOwner") String adsOwner, @Param("fromDate") LocalDate fromDate);
}
