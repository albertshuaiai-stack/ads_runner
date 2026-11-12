package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsClickTrack;
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

    // 按 normaltask 聚合: 发放的点击ID数 + 真实命中数 / issued click ids and real hits per normal task
    @Query("""
            select c.normalAdsId, count(c), coalesce(sum(c.clickCount), 0)
            from AdsClickTrack c
            where c.adsOwner = :adsOwner
            group by c.normalAdsId
            """)
    List<Object[]> aggregateByOwner(@Param("adsOwner") String adsOwner);
}
