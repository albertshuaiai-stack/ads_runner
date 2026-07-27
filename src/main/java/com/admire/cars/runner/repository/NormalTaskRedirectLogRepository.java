package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.NormalTaskRedirectLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface NormalTaskRedirectLogRepository extends JpaRepository<NormalTaskRedirectLog, Long>, JpaSpecificationExecutor<NormalTaskRedirectLog> {
}
