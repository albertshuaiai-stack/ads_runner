package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AdsAccountRepository extends JpaRepository<AdsAccount, Long>, JpaSpecificationExecutor<AdsAccount> {
}
