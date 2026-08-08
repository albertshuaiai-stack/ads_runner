package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsTaskLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface AdsTaskLogRepository extends JpaRepository<AdsTaskLog, Long>, JpaSpecificationExecutor<AdsTaskLog> {


    @Modifying
    @Transactional
    @Query("delete from AdsTaskLog n where n.createDate < :cutoff")
    int deleteByCreateDateBefore(LocalDateTime cutoff);

}
