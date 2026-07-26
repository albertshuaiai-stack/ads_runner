package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ShiftLinkLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface ShiftLinkLogRepository extends JpaRepository<ShiftLinkLog, Long>, JpaSpecificationExecutor<ShiftLinkLog> {

    @Modifying
    @Transactional
    int deleteByCreateDateBefore(LocalDateTime cutoff);
}
