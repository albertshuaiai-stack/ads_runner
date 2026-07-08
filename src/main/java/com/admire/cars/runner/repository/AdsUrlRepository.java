package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdsUrlRepository extends JpaRepository<AdsUrl, Long>, JpaSpecificationExecutor<AdsUrl> {
    List<AdsUrl> findByCapMainName(String capMainName);
    List<AdsUrl> findByCampaignOwner(Long campaignOwner);
    List<AdsUrl> findByCapMainNameAndCampaignOwner(String capMainName, Long campaignOwner);
    List<AdsUrl> findByPlatform(String platform);
    Optional<AdsUrl> findByFullUrl(String fullUrl);
}
