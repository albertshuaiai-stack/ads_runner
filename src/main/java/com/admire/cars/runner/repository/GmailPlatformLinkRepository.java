package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.GmailPlatformLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GmailPlatformLinkRepository extends JpaRepository<GmailPlatformLink, Long>, JpaSpecificationExecutor<GmailPlatformLink> {

    List<GmailPlatformLink> findByAdsOwner(String adsOwner);

    List<GmailPlatformLink> findByAdsOwnerAndUserName(String adsOwner, String userName);
}
