package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ReferUserAgent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferUserAgentRepository extends JpaRepository<ReferUserAgent, Long> {
    List<ReferUserAgent> findByDeviceIgnoreCaseOrderByIdAsc(String device);
}
