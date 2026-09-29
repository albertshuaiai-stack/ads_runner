package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.TrackConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackConversionRepository extends JpaRepository<TrackConversion, Long>, JpaSpecificationExecutor<TrackConversion> {

    // 按订单号幂等处理 / idempotent handling by order number
    Optional<TrackConversion> findByOrderNo(String orderNo);

    List<TrackConversion> findByClickId(String clickId);

    List<TrackConversion> findByAdsOwner(String adsOwner);
}
