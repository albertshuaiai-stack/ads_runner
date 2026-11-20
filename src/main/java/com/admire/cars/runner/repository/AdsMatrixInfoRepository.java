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

    // 跨天(countDate 不是今天)时两个计数先归零再计; countDate 必须放最后赋值, MySQL 按从左到右用新值计算
    // On a new day both counters restart; countDate must be assigned last because MySQL evaluates SET left to right
    @Modifying
    @Transactional
    @Query("""
            update AdsMatrixInfo m
               set m.successCount = case when m.countDate = :today then coalesce(m.successCount, 0) + 1 else 1 end,
                   m.failedCount = case when m.countDate = :today then coalesce(m.failedCount, 0) else 0 end,
                   m.lastSuccessDate = :eventTime,
                   m.updateDate = :eventTime,
                   m.countDate = :today
             where m.id = :id
            """)
    int incrementSuccessCount(Long id, LocalDateTime eventTime, String today);

    @Modifying
    @Transactional
    @Query("""
            update AdsMatrixInfo m
               set m.failedCount = case when m.countDate = :today then coalesce(m.failedCount, 0) + 1 else 1 end,
                   m.successCount = case when m.countDate = :today then coalesce(m.successCount, 0) else 0 end,
                   m.updateDate = :eventTime,
                   m.countDate = :today
             where m.id = :id
            """)
    int incrementFailedCount(Long id, LocalDateTime eventTime, String today);
}
