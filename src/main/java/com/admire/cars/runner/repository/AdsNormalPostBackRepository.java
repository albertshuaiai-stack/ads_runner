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

    // 按 normaltask 聚合下单 / order aggregation per normal task
    @Query("""
            select p.normalAdsId, count(p), coalesce(sum(p.orderAmount), 0),
                   coalesce(sum(p.userCommissionAmount), 0), count(distinct p.clickId)
            from AdsNormalPostBack p
            where p.adsOwner = :adsOwner and p.normalAdsId is not null
            group by p.normalAdsId
            """)
    List<Object[]> aggregateByOwner(@Param("adsOwner") String adsOwner);

    @Query("""
            select count(p) from AdsNormalPostBack p
            where p.adsOwner = :adsOwner and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            """)
    long countUnmatched(@Param("adsOwner") String adsOwner);

    @Query(value = """
            select p from AdsNormalPostBack p
            where p.adsOwner = :adsOwner and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            order by p.id desc
            """,
            countQuery = """
            select count(p) from AdsNormalPostBack p
            where p.adsOwner = :adsOwner and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            """)
    Page<AdsNormalPostBack> findUnmatched(@Param("adsOwner") String adsOwner, Pageable pageable);

    @Query(value = """
            select p from AdsNormalPostBack p
            where p.adsOwner = :adsOwner and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            order by p.id asc
            """,
            countQuery = """
            select count(p) from AdsNormalPostBack p
            where p.adsOwner = :adsOwner and (p.matchStatus is null or p.matchStatus <> 'MATCHED')
            """)
    Page<AdsNormalPostBack> findReattributable(@Param("adsOwner") String adsOwner, Pageable pageable);
}
