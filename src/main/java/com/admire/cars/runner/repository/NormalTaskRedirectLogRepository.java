package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.NormalTaskRedirectLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface NormalTaskRedirectLogRepository extends JpaRepository<NormalTaskRedirectLog, Long>, JpaSpecificationExecutor<NormalTaskRedirectLog> {


    @Modifying
    @Transactional
    @Query("delete from NormalTaskRedirectLog n where n.createDate < :cutoff")
    int deleteByCreateDateBefore(LocalDateTime cutoff);

}
