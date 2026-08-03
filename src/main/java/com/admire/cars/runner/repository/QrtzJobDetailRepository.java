package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.QrtzJobDetail;
import com.admire.cars.runner.entity.QrtzJobDetailId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface QrtzJobDetailRepository extends JpaRepository<QrtzJobDetail, QrtzJobDetailId>, JpaSpecificationExecutor<QrtzJobDetail> {
}
