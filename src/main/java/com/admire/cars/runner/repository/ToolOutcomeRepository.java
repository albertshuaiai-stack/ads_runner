package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ToolOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolOutcomeRepository extends JpaRepository<ToolOutcome, Long>, JpaSpecificationExecutor<ToolOutcome> {
}
