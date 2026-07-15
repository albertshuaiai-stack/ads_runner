package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ToolPaypal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ToolPaypalRepository extends JpaRepository<ToolPaypal, Long>, JpaSpecificationExecutor<ToolPaypal> {
    Optional<ToolPaypal> findByPaypalEmail(String paypalEmail);
}
