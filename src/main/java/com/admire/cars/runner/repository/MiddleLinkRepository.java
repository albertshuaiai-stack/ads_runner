package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.MiddleLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MiddleLinkRepository extends JpaRepository<MiddleLink, Long>, JpaSpecificationExecutor<MiddleLink> {

    List<MiddleLink> findByAdsOwner(String adsOwner);
}
