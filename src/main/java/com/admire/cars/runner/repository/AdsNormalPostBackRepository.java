package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdsNormalPostBackRepository extends JpaRepository<AdsNormalPostBack, Long>, JpaSpecificationExecutor<AdsNormalPostBack> {

    // 显式 JPQL, 避免走派生查询的 PartTreeJpaQuery / JpqlQueryBuilder 构造路径
    // explicit JPQL so this hot path does not depend on JpqlQueryBuilder internals
    @Query("""
            select p from AdsNormalPostBack p
            where p.adsOwner = :adsOwner and p.orderNo = :orderNo
            """)
    Optional<AdsNormalPostBack> findByAdsOwnerAndOrderNo(@Param("adsOwner") String adsOwner,
                                                         @Param("orderNo") String orderNo);

    // 已归因到任务的订单, 供复盘按任务/日期统计 / orders attributed to a task, used by the daily review
    @Query("""
            select p from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and p.normalAdsId is not null
            """)
    List<AdsNormalPostBack> findAttributedByOwner(@Param("adsOwner") String adsOwner);

    // owner 为 null 表示不过滤(admin All owners) / null owner means no filter
    @Query("""
            select p from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and p.clickId = :clickId
            order by p.id desc
            """)
    List<AdsNormalPostBack> findByClickIdWithOwner(@Param("adsOwner") String adsOwner,
                                                   @Param("clickId") String clickId);

    @Query("""
            select p from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
            order by p.id desc
            """)
    List<AdsNormalPostBack> findRecent(@Param("adsOwner") String adsOwner, Pageable pageable);

    // 按任务聚合, 含退单数; 退单按状态关键词识别 / per-task aggregate including refunded count by status keywords
    @Query("""
            select p.normalAdsId, count(p), coalesce(sum(p.orderAmount), 0),
                   coalesce(sum(p.userCommissionAmount), 0), count(distinct p.clickId),
                   sum(case when lower(coalesce(p.status, '')) like '%reject%'
                         or lower(coalesce(p.status, '')) like '%refund%'
                         or lower(coalesce(p.status, '')) like '%cancel%'
                         or lower(coalesce(p.status, '')) like '%void%'
                         or lower(coalesce(p.status, '')) like '%decline%'
                         or lower(coalesce(p.status, '')) like '%revers%'
                         then 1 else 0 end)
            from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and p.normalAdsId is not null
            group by p.normalAdsId
            """)
    List<Object[]> aggregateByOwner(@Param("adsOwner") String adsOwner);

    @Query("""
            select count(p) from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            """)
    long countUnmatched(@Param("adsOwner") String adsOwner);

    // 全局口径含未归因订单, 含退单数 / global totals include unmatched orders and refunded count
    @Query("""
            select count(p), coalesce(sum(p.orderAmount), 0), coalesce(sum(p.userCommissionAmount), 0),
                   sum(case when lower(coalesce(p.status, '')) like '%reject%'
                         or lower(coalesce(p.status, '')) like '%refund%'
                         or lower(coalesce(p.status, '')) like '%cancel%'
                         or lower(coalesce(p.status, '')) like '%void%'
                         or lower(coalesce(p.status, '')) like '%decline%'
                         or lower(coalesce(p.status, '')) like '%revers%'
                         then 1 else 0 end)
            from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
            """)
    List<Object[]> aggregateTotal(@Param("adsOwner") String adsOwner);

    // 按任务+状态统计订单数 / order count per task and status
    @Query("""
            select p.normalAdsId, lower(trim(coalesce(p.status, ''))), count(p)
            from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and p.normalAdsId is not null
            group by p.normalAdsId, lower(trim(coalesce(p.status, '')))
            """)
    List<Object[]> countByTaskAndStatus(@Param("adsOwner") String adsOwner);

    // 全部订单按状态统计(含未归因) / order count per status, unmatched included
    @Query("""
            select lower(trim(coalesce(p.status, ''))), count(p)
            from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
            group by lower(trim(coalesce(p.status, '')))
            """)
    List<Object[]> countByStatus(@Param("adsOwner") String adsOwner);

    // 未归因订单按商家分组 / unmatched orders grouped by advertiser
    @Query("""
            select p.advertiserShopName, p.advertiserShopId, count(p),
                   coalesce(sum(p.orderAmount), 0), coalesce(sum(p.userCommissionAmount), 0)
            from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            group by p.advertiserShopName, p.advertiserShopId
            """)
    List<Object[]> findUnmatchedGroupedByAdvertiser(@Param("adsOwner") String adsOwner);

    // 按点击ID聚合订单, 供 Click 区展示 / orders aggregated per click id
    @Query("""
            select p.clickId, count(p), coalesce(sum(p.orderAmount), 0),
                   coalesce(sum(p.userCommissionAmount), 0)
            from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and p.clickId is not null
            group by p.clickId
            """)
    List<Object[]> aggregateByClickId(@Param("adsOwner") String adsOwner);

    // 按联盟平台聚合, 支持后续接入更多平台 / aggregate by affiliate network
    @Query("""
            select p.affiliateSite, count(p), coalesce(sum(p.orderAmount), 0),
                   coalesce(sum(p.userCommissionAmount), 0)
            from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
            group by p.affiliateSite
            """)
    List<Object[]> aggregateByPlatform(@Param("adsOwner") String adsOwner);

    @Query("""
            select count(p) from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner) and p.subId is not null
            """)
    long countWithSubId(@Param("adsOwner") String adsOwner);

    @Query("""
            select count(p) from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner) and p.matchStatus = 'MATCHED'
            """)
    long countMatched(@Param("adsOwner") String adsOwner);

    @Query("""
            select p.subId from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner) and p.subId is not null
            order by p.id desc
            """)
    List<String> findRecentSubIds(@Param("adsOwner") String adsOwner, Pageable pageable);

    @Query(value = """
            select p from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            order by p.id desc
            """,
            countQuery = """
            select count(p) from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            """)
    Page<AdsNormalPostBack> findUnmatched(@Param("adsOwner") String adsOwner, Pageable pageable);

    @Query(value = """
            select p from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            order by p.id asc
            """,
            countQuery = """
            select count(p) from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
              and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            """)
    Page<AdsNormalPostBack> findReattributable(@Param("adsOwner") String adsOwner, Pageable pageable);
}
