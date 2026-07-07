package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsUrlAud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AdsUrlAudRepository extends JpaRepository<AdsUrlAud, Long> {
    List<AdsUrlAud> findById(Long id);
    List<AdsUrlAud> findByCapMainName(String capMainName);
}
