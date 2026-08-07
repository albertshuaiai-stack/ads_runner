package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AffiliateAds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AffiliateAdsRepository extends JpaRepository<AffiliateAds, Long>, JpaSpecificationExecutor<AffiliateAds> {
    long deleteByAffiliateNetworkAndAdsOwnerAndRegion(String affiliateNetwork, String adsOwner, String region);
}
