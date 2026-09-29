package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.TrackHealthCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackHealthCheckRepository extends JpaRepository<TrackHealthCheck, Long>, JpaSpecificationExecutor<TrackHealthCheck> {

    List<TrackHealthCheck> findBySlug(String slug);

    List<TrackHealthCheck> findByAdsOwner(String adsOwner);
}
