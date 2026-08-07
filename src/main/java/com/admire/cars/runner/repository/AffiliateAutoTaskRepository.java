package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AffiliateAutoTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AffiliateAutoTaskRepository extends JpaRepository<AffiliateAutoTask, Long>, JpaSpecificationExecutor<AffiliateAutoTask> {
}
