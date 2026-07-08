package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdsPlatformRepository extends JpaRepository<AdsPlatform, Long> {
    Optional<AdsPlatform> findByPlatformNameIgnoreCase(String platformName);
    boolean existsByPlatformNameIgnoreCase(String platformName);
}
