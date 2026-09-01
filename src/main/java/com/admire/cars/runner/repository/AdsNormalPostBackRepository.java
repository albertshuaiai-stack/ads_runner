package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsNormalPostBack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdsNormalPostBackRepository extends JpaRepository<AdsNormalPostBack, Long>, JpaSpecificationExecutor<AdsNormalPostBack> {


    Optional<AdsNormalPostBack> findByAdsOwnerAndOrderNo(String adsOwner, String orderNo);
}
