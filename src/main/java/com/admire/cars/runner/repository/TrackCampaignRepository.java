package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.TrackCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackCampaignRepository extends JpaRepository<TrackCampaign, Long>, JpaSpecificationExecutor<TrackCampaign> {

    // 跳转端点按 slug 查找 / lookup by slug for redirect endpoint
    Optional<TrackCampaign> findBySlug(String slug);

    List<TrackCampaign> findByAdsOwner(String adsOwner);

    List<TrackCampaign> findByAdsOwnerAndStatus(String adsOwner, String status);

    // 脚本按 campaign 名取追踪配置 / lookup by campaign name for ads scripts
    List<TrackCampaign> findByAdsOwnerAndCampaignName(String adsOwner, String campaignName);
}
