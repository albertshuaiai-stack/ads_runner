package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ShiftLinkHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftLinkHistoryRepository extends JpaRepository<ShiftLinkHistory, Long> {

    List<ShiftLinkHistory> findByAdsOwnerOrderByUserNameAscPlatformNameAscAdsNameAsc(String adsOwner);

    List<ShiftLinkHistory> findAllByOrderByUserNameAscPlatformNameAscAdsNameAsc();

    Optional<ShiftLinkHistory> findByAdsOwnerAndUserNameAndPlatformNameAndAdsName(
            String adsOwner, String userName, String platformName, String adsName);
}
