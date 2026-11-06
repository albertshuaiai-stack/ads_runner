package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ShiftLinkStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftLinkStatRepository extends JpaRepository<ShiftLinkStat, Long> {

    List<ShiftLinkStat> findByAdsOwner(String adsOwner);

    Optional<ShiftLinkStat> findByAdsOwnerAndUserNameAndPlatformNameAndAdsName(
            String adsOwner, String userName, String platformName, String adsName);
}
