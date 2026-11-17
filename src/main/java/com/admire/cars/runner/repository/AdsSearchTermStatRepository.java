package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsSearchTermStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface AdsSearchTermStatRepository extends JpaRepository<AdsSearchTermStat, Long> {

    @Modifying
    @Query("""
            delete from AdsSearchTermStat t
            where t.adsOwner = :adsOwner and t.snapshotDate = :snapshotDate
              and t.campaignName in :campaignNames
            """)
    int deleteSnapshot(@Param("adsOwner") String adsOwner,
                       @Param("snapshotDate") LocalDate snapshotDate,
                       @Param("campaignNames") Collection<String> campaignNames);

    // owner 为 null 表示全部(admin) / null owner means all owners
    @Query("""
            select t from AdsSearchTermStat t
            where (:adsOwner is null or t.adsOwner = :adsOwner)
              and t.snapshotDate >= :fromDate
            """)
    List<AdsSearchTermStat> findSince(@Param("adsOwner") String adsOwner, @Param("fromDate") LocalDate fromDate);
}
