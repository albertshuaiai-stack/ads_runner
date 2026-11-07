package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ShiftLinkStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftLinkStatRepository extends JpaRepository<ShiftLinkStat, Long> {

    List<ShiftLinkStat> findByAdsOwner(String adsOwner);

    Optional<ShiftLinkStat> findByAdsOwnerAndUserNameAndPlatformNameAndAdsName(
            String adsOwner, String userName, String platformName, String adsName);

    // 原子累加计数，避免并发冲突 / atomic counter upsert safe under concurrency
    @Modifying
    @Query(value = """
            INSERT INTO SHIFT_LINK_STAT (ADS_OWNER, USER_NAME, PLATFORM_NAME, ADS_NAME, LINK_COUNT, SAMPLE_URL, CREATE_DATE, UPDATE_DATE)
            VALUES (:adsOwner, :userName, :platformName, :adsName, :delta, :sampleUrl, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE LINK_COUNT = LINK_COUNT + :delta, UPDATE_DATE = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsertIncrement(String adsOwner, String userName, String platformName, String adsName, long delta, String sampleUrl);
}
