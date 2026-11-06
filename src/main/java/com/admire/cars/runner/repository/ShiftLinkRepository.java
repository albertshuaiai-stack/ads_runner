package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsTaskLog;
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
    List<ShiftLink> findByAdsOwnerAndUserName(String adsOwner, String userName);

    List<ShiftLink> findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(String adsOwner, String adsName, String adsType);

    Optional<ShiftLink> findTopByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberDesc(String adsOwner, String adsName, String adsType);
    @Query("""
            select s from ShiftLink s
            where s.adsOwner = :adsOwner
              and s.adsName = :adsName
              and s.adsType = :adsType
              and upper(s.status) = upper(:status)
              and coalesce(s.displayTimes, 0) < coalesce(s.displayNumber, 0)
              order by (s.displayNumber - s.displayTimes) desc, s.id DESC
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

    @Query("""
            select s from ShiftLink s
            where s.adsOwner = :adsOwner
              and s.adsName = :adsName
              and s.adsType = :adsType
              ORDER BY s.createDate DESC LIMIT 1
            """)
    ShiftLink findLastShiftLink(String adsOwner, String adsName, String adsType);

    List<ShiftLink> findByPlatformName(String platformName);

    Optional<ShiftLink> findByFullUrl(String fullUrl);

    // URL 去重 / URL de-duplication
    List<ShiftLink> findByAdsOwnerAndFullUrl(String adsOwner, String fullUrl);

    // 按 Campaign / Platform 整体删除 / bulk delete by campaign or platform
    List<ShiftLink> findByAdsName(String adsName);

    List<ShiftLink> findByAdsOwnerAndAdsName(String adsOwner, String adsName);

    List<ShiftLink> findByAdsOwnerAndPlatformName(String adsOwner, String platformName);

    // Gmail -> 平台 -> 商家 统计，max(fullUrl) 作为商家样例链接
    // stats grouped by gmail, platform and merchant; max(fullUrl) as a sample link per merchant
    @Query("select s.userName, s.platformName, s.adsName, count(s), max(s.fullUrl) from ShiftLink s group by s.userName, s.platformName, s.adsName")
    List<Object[]> summarizeByGmailPlatformMerchant();

    @Query("select s.userName, s.platformName, s.adsName, count(s), max(s.fullUrl) from ShiftLink s where s.adsOwner = :adsOwner group by s.userName, s.platformName, s.adsName")
    List<Object[]> summarizeByGmailPlatformMerchantAndOwner(String adsOwner);

    // 过期数据聚合：清理前归档 / aggregate expired rows before cleanup
    @Query("select s.adsOwner, s.userName, s.platformName, s.adsName, count(s), max(s.fullUrl) from ShiftLink s where s.createDate < :cutoff and upper(s.adsType) = upper(:adsType) group by s.adsOwner, s.userName, s.platformName, s.adsName")
    List<Object[]> aggregateExpiredByAdsType(LocalDateTime cutoff, String adsType);

    // Matrix 绝对过期清理：不看消耗进度 / absolute matrix cleanup regardless of consumption
    @Modifying
    @Transactional
    @Query("delete from ShiftLink s where s.createDate < :cutoff and upper(s.adsType) = 'MATRIX'")
    int deleteMatrixByCreateDateBefore(LocalDateTime cutoff);

    @Modifying
    @Transactional
    @Query("delete from ShiftLink s where s.createDate < :cutoff and upper(s.adsType) = 'NORMAL'")
    int deleteByCreateDateBeforeAndAdsTypeNormal(LocalDateTime cutoff);

    @Modifying
    @Transactional
    @Query("delete from ShiftLink s where (s.displayNumber - s.displayTimes) = 0 and s.createDate < :cutoff and upper(s.adsType) = 'MATRIX'")
    int deleteByCreateDateBeforeAndAdsTypeMatrix(LocalDateTime cutoff);

}
