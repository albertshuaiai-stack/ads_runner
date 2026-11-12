package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsClickTrack;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdsClickTrackRepository extends JpaRepository<AdsClickTrack, Long> {

    Optional<AdsClickTrack> findByClickId(String clickId);

    List<AdsClickTrack> findByAdsOwnerOrderByIdDesc(String adsOwner);

    List<AdsClickTrack> findByAdsOwnerAndNormalAdsIdOrderByIdDesc(String adsOwner, Long normalAdsId);

    // owner 为 null 表示不过滤(admin All owners) / null owner means no filter
    @Query("""
            select c from AdsClickTrack c
            where (:adsOwner is null or c.adsOwner = :adsOwner)
            order by c.id desc
            """)
    List<AdsClickTrack> findRecent(@Param("adsOwner") String adsOwner, Pageable pageable);

    @Query("""
            select c.normalAdsId, count(c), coalesce(sum(c.clickCount), 0)
            from AdsClickTrack c
            where (:adsOwner is null or c.adsOwner = :adsOwner)
            group by c.normalAdsId
            """)
    List<Object[]> aggregateByOwner(@Param("adsOwner") String adsOwner);

    @Query("""
            select count(c) from AdsClickTrack c
            where (:adsOwner is null or c.adsOwner = :adsOwner)
            """)
    long countByOwner(@Param("adsOwner") String adsOwner);
}
