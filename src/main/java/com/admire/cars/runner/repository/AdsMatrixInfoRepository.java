package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsMatrixInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AdsMatrixInfoRepository extends JpaRepository<AdsMatrixInfo, Long>, JpaSpecificationExecutor<AdsMatrixInfo> {
    Long countByAdsOwner(String adsOwner);
    
    Optional<AdsMatrixInfo> findByCampainNameAndAdsOwner(String campainName, String adsOwner);

    @Modifying
    @Transactional
    @Query("""
            update AdsMatrixInfo m
               set m.successCount = coalesce(m.successCount, 0) + 1,
                   m.lastSuccessDate = :eventTime,
                   m.updateDate = :eventTime
             where m.id = :id
            """)
    int incrementSuccessCount(Long id, LocalDateTime eventTime);

    @Modifying
    @Transactional
    @Query("""
            update AdsMatrixInfo m
               set m.failedCount = coalesce(m.failedCount, 0) + 1,
                   m.updateDate = :eventTime
             where m.id = :id
            """)
    int incrementFailedCount(Long id, LocalDateTime eventTime);


    @Modifying
    @Transactional
    @Query("update AdsMatrixInfo m set m.successCount = 0, m.failedCount = 0")
    int revertSuccessAndFailedCount();
}
