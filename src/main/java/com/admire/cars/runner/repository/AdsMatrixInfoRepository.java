package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsMatrixInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AdsMatrixInfoRepository extends JpaRepository<AdsMatrixInfo, Long>, JpaSpecificationExecutor<AdsMatrixInfo> {
}
