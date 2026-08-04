package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AffiliateAdsSyncConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AffiliateAdsSyncConfigRepository extends JpaRepository<AffiliateAdsSyncConfig, Long>, JpaSpecificationExecutor<AffiliateAdsSyncConfig> {
}
