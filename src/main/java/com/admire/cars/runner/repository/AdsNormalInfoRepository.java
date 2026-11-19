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

    // 同名任务可能不止一个, 用列表避免唯一结果异常 / several tasks may share a name, so return a list
    List<AdsNormalInfo> findByAdsOwnerAndCampainNameOrderByIdDesc(String adsOwner, String campainName);

    // 后台已上架的 normaltask 列表 / listed normal tasks
    List<AdsNormalInfo> findByAdsOwnerOrderByIdDesc(String adsOwner);

    List<AdsNormalInfo> findAllByOrderByIdDesc();


    @Modifying
    @Transactional
    @Query("update AdsNormalInfo s set s.successCount = 0, s.failedCount = 0")
    int revertSuccessAndFailedCount();
}

