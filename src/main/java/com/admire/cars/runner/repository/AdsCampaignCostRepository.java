package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsCampaignCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdsCampaignCostRepository extends JpaRepository<AdsCampaignCost, Long> {

    Optional<AdsCampaignCost> findByAdsOwnerAndCampaignKey(String adsOwner, String campaignKey);

    // 同名下是否已有带 campaign id 的记录(新脚本已接管) / whether the new script already reports this name with ids
    boolean existsByAdsOwnerAndCampaignNameAndCampaignIdIsNotNull(String adsOwner, String campaignName);

    List<AdsCampaignCost> findByAdsOwner(String adsOwner);
}
