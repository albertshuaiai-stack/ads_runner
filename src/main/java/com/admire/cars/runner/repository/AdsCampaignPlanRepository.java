package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.AdsCampaignPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdsCampaignPlanRepository extends JpaRepository<AdsCampaignPlan, Long> {

    // owner 为 null 表示全部(admin) / null owner means all owners
    @Query("""
            select p from AdsCampaignPlan p
            where (:adsOwner is null or p.adsOwner = :adsOwner)
            order by p.id desc
            """)
    List<AdsCampaignPlan> findByOwner(@Param("adsOwner") String adsOwner);

    @Query("""
            select p from AdsCampaignPlan p
            where p.adsOwner = :adsOwner and p.campaignName = :campaignName
            """)
    Optional<AdsCampaignPlan> findByOwnerAndCampaign(@Param("adsOwner") String adsOwner,
                                                     @Param("campaignName") String campaignName);
}
