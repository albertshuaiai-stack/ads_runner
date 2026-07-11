package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ToolEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ToolEmailRepository extends JpaRepository<ToolEmail, Long>, JpaSpecificationExecutor<ToolEmail> {
    Optional<ToolEmail> findByEmailAddress(String emailAddress);

    Optional<ToolEmail> findByUserName(String userName);
}
