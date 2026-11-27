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

    // 每个 campaign 首个有花费的日期: [adsOwner, campaignName, minDate] / first date with spend per campaign
    @Query("""
            select s.adsOwner, s.campaignName, min(s.statDate) from AdsDailyStat s
            where (:adsOwner is null or s.adsOwner = :adsOwner)
              and s.cost > 0
            group by s.adsOwner, s.campaignName
            """)
    List<Object[]> findFirstSpendDates(@Param("adsOwner") String adsOwner);

    // 每个 owner 最早一条数据的日期: [adsOwner, minDate] / earliest stat date per owner
    @Query("""
            select s.adsOwner, min(s.statDate) from AdsDailyStat s
            where (:adsOwner is null or s.adsOwner = :adsOwner)
            group by s.adsOwner
            """)
    List<Object[]> findFirstStatDates(@Param("adsOwner") String adsOwner);
}
