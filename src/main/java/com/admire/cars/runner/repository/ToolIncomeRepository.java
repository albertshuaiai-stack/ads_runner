package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ToolIncome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolIncomeRepository extends JpaRepository<ToolIncome, Long>, JpaSpecificationExecutor<ToolIncome> {
}
