package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AffiliateAdsSync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AffiliateAdsSyncRepository extends JpaRepository<AffiliateAdsSync, Long>, JpaSpecificationExecutor<AffiliateAdsSync> {
    long deleteByAffiliateNetworkAndAdsOwnerAndRegion(String affiliateNetwork, String adsOwner, String region);
}
