package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AffiliateAdsSyncTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AffiliateAdsSyncTaskRepository extends JpaRepository<AffiliateAdsSyncTask, Long>, JpaSpecificationExecutor<AffiliateAdsSyncTask> {
}
