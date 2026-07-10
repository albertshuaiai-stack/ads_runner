package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsMatrixInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdsMatrixInfoRepository extends JpaRepository<AdsMatrixInfo, Long>, JpaSpecificationExecutor<AdsMatrixInfo> {
    Long countByAdsOwner(String adsOwner);
    
    Optional<AdsMatrixInfo> findByCampainNameAndAdsOwner(String campainName, String adsOwner);
}
