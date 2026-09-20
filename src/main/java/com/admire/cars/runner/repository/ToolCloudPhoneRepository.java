package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ToolCloudPhone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ToolCloudPhoneRepository extends JpaRepository<ToolCloudPhone, Long>, JpaSpecificationExecutor<ToolCloudPhone> {
    boolean existsByPhoneNumberIgnoreCase(String phoneNumber);
}
