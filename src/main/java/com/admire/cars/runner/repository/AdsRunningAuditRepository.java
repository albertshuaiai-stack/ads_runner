package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsRunningAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AdsRunningAuditRepository extends JpaRepository<AdsRunningAudit, Long>, JpaSpecificationExecutor<AdsRunningAudit> {

    long countByPlatformIgnoreCaseAndEmailIgnoreCaseAndBrandIgnoreCaseAndAdsOwnerAndCreateDateBetween(
            String platform, String email, String brand, String adsOwner, LocalDateTime start, LocalDateTime end);

    void deleteByPlatformIgnoreCaseAndEmailIgnoreCaseAndBrandIgnoreCaseAndAdsOwnerAndCreateDateBetween(
            String platform, String email, String brand, String adsOwner, LocalDateTime start, LocalDateTime end);
}
