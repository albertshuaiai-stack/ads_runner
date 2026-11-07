package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ShiftLinkSeenUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ShiftLinkSeenUrlRepository extends JpaRepository<ShiftLinkSeenUrl, Long> {

    // 新 URL 返回 1，重复 URL 返回 0 / returns 1 for a new url, 0 for a duplicate
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO SHIFT_LINK_SEEN_URL (ADS_OWNER, URL_HASH, CREATE_DATE)
            VALUES (:adsOwner, :urlHash, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertIgnore(String adsOwner, String urlHash);
}
