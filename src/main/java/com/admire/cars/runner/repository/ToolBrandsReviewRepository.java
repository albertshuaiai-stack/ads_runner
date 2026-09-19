package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ToolBrandsReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolBrandsReviewRepository extends JpaRepository<ToolBrandsReview, Long>, JpaSpecificationExecutor<ToolBrandsReview> {

    @Query("select distinct t.brand from ToolBrandsReview t where t.brand is not null order by t.brand asc")
    List<String> findDistinctBrandsOrderByBrandAsc();

    @Query("select distinct t.brand from ToolBrandsReview t where lower(t.adsOwner) = lower(:adsOwner) and t.brand is not null order by t.brand asc")
    List<String> findDistinctBrandsByAdsOwnerOrderByBrandAsc(@Param("adsOwner") String adsOwner);
}
