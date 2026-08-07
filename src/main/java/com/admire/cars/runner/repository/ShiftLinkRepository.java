package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ShiftLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftLinkRepository extends JpaRepository<ShiftLink, Long>, JpaSpecificationExecutor<ShiftLink> {
    List<ShiftLink> findByAdsIdAndAdsType(Long adsId, String adsType);
    List<ShiftLink> findByAdsOwner(String adsOwner);
    List<ShiftLink> findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(String adsOwner, String adsName, String adsType);
    Optional<ShiftLink> findTopByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberDesc(String adsOwner, String adsName, String adsType);
    @Query("""
            select s from ShiftLink s
            where s.adsOwner = :adsOwner
              and s.adsName = :adsName
              and s.adsType = :adsType
              and upper(s.status) = upper(:status)
              and coalesce(s.displayTimes, 0) < coalesce(s.displayNumber, 0)
            order by (coalesce(s.displayNumber, 0) - coalesce(s.displayTimes, 0)) DESC
            """)
    List<ShiftLink> findEligibleForConsume(String adsOwner, String adsName, String adsType, String status);

    @Query("""
            select s from ShiftLink s
            where s.adsOwner = :adsOwner
              and s.adsName = :adsName
              and s.adsType = :adsType
              and upper(s.status) = upper(:status)
              and coalesce(s.displayTimes, 0) = 0
             order by s.id DESC
            """)
    List<ShiftLink> findEligibleForNormalConsume(String adsOwner, String adsName, String adsType, String status);

    List<ShiftLink> findByPlatformName(String platformName);
    Optional<ShiftLink> findByFullUrl(String fullUrl);

    // URL 去重 / URL de-duplication
    List<ShiftLink> findByAdsOwnerAndFullUrl(String adsOwner, String fullUrl);

    // 按 Campaign / Platform 整体删除 / bulk delete by campaign or platform
    List<ShiftLink> findByAdsName(String adsName);
    List<ShiftLink> findByAdsOwnerAndAdsName(String adsOwner, String adsName);
    List<ShiftLink> findByAdsOwnerAndPlatformName(String adsOwner, String platformName);

    @Modifying
    @Transactional
    @Query("delete from ShiftLink s where s.createDate < :cutoff and upper(s.adsType) = 'NORMAL'")
    int deleteByCreateDateBeforeAndAdsTypeNormal(LocalDateTime cutoff);
}
