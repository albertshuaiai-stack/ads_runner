package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AffiliateAdsTestTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AffiliateAdsTestTaskRepository extends JpaRepository<AffiliateAdsTestTask, Long>, JpaSpecificationExecutor<AffiliateAdsTestTask> {
}
