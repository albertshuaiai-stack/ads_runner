package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsMatrixAffiliateInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdsMatrixAffiliateInfoRepository extends JpaRepository<AdsMatrixAffiliateInfo, Long> {
}
