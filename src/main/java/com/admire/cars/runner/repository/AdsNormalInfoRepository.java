package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsNormalInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdsNormalInfoRepository extends JpaRepository<AdsNormalInfo, Long>, JpaSpecificationExecutor<AdsNormalInfo> {
    Long countByAdsOwner(String adsOwner);

    Optional<AdsNormalInfo> findByCampainNameAndAdsOwner(String campainName, String adsOwner);

    // 后台已上架的 normaltask 列表 / listed normal tasks
    List<AdsNormalInfo> findByAdsOwnerOrderByIdDesc(String adsOwner);


    @Modifying
    @Transactional
    @Query("update AdsNormalInfo s set s.successCount = 0, s.failedCount = 0")
    int revertSuccessAndFailedCount();
}

