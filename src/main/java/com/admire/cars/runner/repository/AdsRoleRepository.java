package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdsRoleRepository extends JpaRepository<AdsRole, Long> {
    Optional<AdsRole> findByRoleNameIgnoreCase(String roleName);
    boolean existsByRoleNameIgnoreCase(String roleName);
}
