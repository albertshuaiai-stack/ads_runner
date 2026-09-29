package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.TrackClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrackClickRepository extends JpaRepository<TrackClick, Long>, JpaSpecificationExecutor<TrackClick> {

    // 回传时按 clickId 归因 / attribution lookup by click id
    Optional<TrackClick> findByClickId(String clickId);

    List<TrackClick> findByAdsOwner(String adsOwner);

    List<TrackClick> findBySlug(String slug);

    List<TrackClick> findByClickTimeBetween(LocalDateTime start, LocalDateTime end);
}
