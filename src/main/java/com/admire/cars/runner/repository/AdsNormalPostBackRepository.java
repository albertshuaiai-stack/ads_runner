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

    Optional<AdsNormalPostBack> findByAdsOwnerAndOrderNo(String adsOwner, String orderNo);

    List<AdsNormalPostBack> findByAdsOwnerAndClickIdOrderByIdDesc(String adsOwner, String clickId);

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

    @Query("""
            select p.normalAdsId, count(p), coalesce(sum(p.orderAmount), 0),
                   coalesce(sum(p.userCommissionAmount), 0), count(distinct p.clickId)
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

    // 全局口径含未归因订单 / global totals include unmatched orders
    @Query("""
            select count(p), coalesce(sum(p.orderAmount), 0), coalesce(sum(p.userCommissionAmount), 0)
            from AdsNormalPostBack p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
            """)
    List<Object[]> aggregateTotal(@Param("adsOwner") String adsOwner);

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
