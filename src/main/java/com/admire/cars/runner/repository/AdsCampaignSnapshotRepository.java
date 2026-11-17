package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsCampaignSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdsCampaignSnapshotRepository extends JpaRepository<AdsCampaignSnapshot, Long> {

    // owner 为 null 表示全部(admin) / null owner means all owners
    @Query("""
            select s from AdsCampaignSnapshot s
            where (:adsOwner is null or s.adsOwner = :adsOwner)
            """)
    List<AdsCampaignSnapshot> findByOwner(@Param("adsOwner") String adsOwner);
}
