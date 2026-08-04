package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AffiliateAdsTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AffiliateAdsTestResultRepository extends JpaRepository<AffiliateAdsTestResult, Long>, JpaSpecificationExecutor<AffiliateAdsTestResult> {
}
