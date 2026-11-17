package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsTaskLog;
import com.admire.cars.runner.entity.ShiftLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Matrix 绝对过期清理：不看消耗进度 / absolute matrix cleanup regardless of consumption
    @Modifying
    @Transactional
    @Query("delete from ShiftLink s where s.createDate < :cutoff and upper(s.adsType) = 'MATRIX'")
    int deleteMatrixByCreateDateBefore(LocalDateTime cutoff);

    @Modifying
    @Transactional
    @Query("delete from ShiftLink s where s.createDate < :cutoff and upper(s.adsType) = 'NORMAL'")
    int deleteByCreateDateBeforeAndAdsTypeNormal(LocalDateTime cutoff);

    // 点击达到全局阈值的 Matrix 链视为用完 / matrix links whose clicks reached the global threshold
    @Modifying
    @Transactional
    @Query("delete from ShiftLink s where coalesce(s.displayTimes, 0) >= :capacity and s.createDate < :cutoff and upper(s.adsType) = 'MATRIX'")
    int deleteByCreateDateBeforeAndAdsTypeMatrix(LocalDateTime cutoff, long capacity);

    // Matrix 每日配额: 按 owner + campaign + type 统计当天链, 与换链池维度一致
    // count today's links by owner, campaign and type, matching the rotation pool scope
    @Query("select count(s) from ShiftLink s where s.adsOwner = :adsOwner and s.adsName = :adsName and upper(s.adsType) = 'MATRIX' and s.createDate >= :dayStart")
    long countMatrixCreatedSince(@Param("adsOwner") String adsOwner, @Param("adsName") String adsName,
                                 @Param("dayStart") LocalDateTime dayStart);

    // Matrix 次日清理: 删除该 campaign 当天之前生成的链 / purge links created before today for this campaign
    @Modifying
    @Transactional
    @Query("delete from ShiftLink s where s.adsOwner = :adsOwner and s.adsName = :adsName and upper(s.adsType) = 'MATRIX' and s.createDate < :dayStart")
    int deleteMatrixCreatedBefore(@Param("adsOwner") String adsOwner, @Param("adsName") String adsName,
                                  @Param("dayStart") LocalDateTime dayStart);

    // Normal 每日配额: 统计该 campaign 当天已生成的链 / count links generated today for this campaign
    @Query("select count(s) from ShiftLink s where s.adsOwner = :adsOwner and s.adsName = :adsName and upper(s.adsType) = 'NORMAL' and s.createDate >= :dayStart")
    long countNormalCreatedSince(@Param("adsOwner") String adsOwner, @Param("adsName") String adsName,
                                 @Param("dayStart") LocalDateTime dayStart);

}
