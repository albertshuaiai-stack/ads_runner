package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.RanSiteIdPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RanSiteIdPoolRepository extends JpaRepository<RanSiteIdPool, Long>, JpaSpecificationExecutor<RanSiteIdPool> {

    // 按池编码取可用 siteId / active site ids of a pool
    List<RanSiteIdPool> findByPoolCodeAndStatus(String poolCode, String status);

    List<RanSiteIdPool> findByAdsOwner(String adsOwner);

    List<RanSiteIdPool> findByPoolCode(String poolCode);

    Optional<RanSiteIdPool> findByPoolCodeAndSiteId(String poolCode, String siteId);
}
