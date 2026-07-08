package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsNormalInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AdsNormalInfoRepository extends JpaRepository<AdsNormalInfo, Long>, JpaSpecificationExecutor<AdsNormalInfo> {
}
