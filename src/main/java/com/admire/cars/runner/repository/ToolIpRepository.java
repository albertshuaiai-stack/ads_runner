package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ToolIp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolIpRepository extends JpaRepository<ToolIp, Long>, JpaSpecificationExecutor<ToolIp> {
    boolean existsByIpIgnoreCase(String ip);
}
