package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsNormalInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AdsNormalInfoRepository extends JpaRepository<AdsNormalInfo, Long>, JpaSpecificationExecutor<AdsNormalInfo> {
    Long countByAdsOwner(String adsOwner);
    
    Optional<AdsNormalInfo> findByCampainNameAndAdsOwner(String campainName, String adsOwner);


    @Modifying
    @Transactional
    @Query("update AdsNormalInfo s set s.successCount = 0, s.failedCount = 0")
    int revertSuccessAndFailedCount();
}

