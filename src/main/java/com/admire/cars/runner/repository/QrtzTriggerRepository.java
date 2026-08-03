package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.QrtzTrigger;
import com.admire.cars.runner.entity.QrtzTriggerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface QrtzTriggerRepository extends JpaRepository<QrtzTrigger, QrtzTriggerId>, JpaSpecificationExecutor<QrtzTrigger> {
}
