package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ToolAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolAccountRepository extends JpaRepository<ToolAccount, Long>, JpaSpecificationExecutor<ToolAccount> {
}
