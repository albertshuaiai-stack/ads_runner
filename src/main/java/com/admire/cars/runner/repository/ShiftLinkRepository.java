package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ShiftLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftLinkRepository extends JpaRepository<ShiftLink, Long>, JpaSpecificationExecutor<ShiftLink> {
    List<ShiftLink> findByAdsIdAndAdsType(Long adsId, String adsType);
    List<ShiftLink> findByAdsOwner(String adsOwner);
    List<ShiftLink> findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(String adsOwner, String adsName, String adsType);
    @Query("""
            select s from ShiftLink s
            where s.adsOwner = :adsOwner
              and s.adsName = :adsName
              and s.adsType = :adsType
              and upper(s.status) = upper(:status)
              and coalesce(s.displayTimes, 0) < coalesce(s.displayNumber, 0)
            order by s.seqNumber asc, s.id asc
            """)
    List<ShiftLink> findEligibleForConsume(String adsOwner, String adsName, String adsType, String status);
    List<ShiftLink> findByPlatformName(String platformName);
    Optional<ShiftLink> findByFullUrl(String fullUrl);
}
