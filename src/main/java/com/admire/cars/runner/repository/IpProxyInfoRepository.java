package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.IpProxyInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IpProxyInfoRepository extends JpaRepository<IpProxyInfo, Long>, JpaSpecificationExecutor<IpProxyInfo> {
    List<IpProxyInfo> findByAdsOwnerAndStatusIgnoreCaseOrderByIdDesc(String adsOwner, String status);
}
