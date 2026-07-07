package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.UserAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserAuditRepository extends JpaRepository<UserAudit, Long> {
    List<UserAudit> findByUserIdOrderByOperationDateDesc(Long userId);
}
