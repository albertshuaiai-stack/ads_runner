package com.admire.cars.runner.repository;

import com.admire.cars.runner.entity.ShiftLinkAud;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShiftLinkAudRepository extends JpaRepository<ShiftLinkAud, Long> {
    List<ShiftLinkAud> findByShiftLinkIdOrderByOperationDateDesc(Long shiftLinkId);
    Optional<ShiftLinkAud> findFirstByAdsOwnerAndAdsNameAndAdsTypeOrderByOperationDateDesc(String adsOwner, String adsName, String adsType);

    @Query(value = """
            select a
            from ShiftLinkAud a
            left join ShiftLink s on s.id = a.shiftLinkId
            where (:adsType is null or lower(a.adsType) like lower(concat('%', :adsType, '%')))
              and (:platformName is null or lower(s.platformName) like lower(concat('%', :platformName, '%')))
              and (:campainName is null or lower(a.adsName) like lower(concat('%', :campainName, '%')))
              and (:adsOwner is null or lower(a.adsOwner) like lower(concat('%', :adsOwner, '%')))
            """,
            countQuery = """
            select count(a)
            from ShiftLinkAud a
            left join ShiftLink s on s.id = a.shiftLinkId
            where (:adsType is null or lower(a.adsType) like lower(concat('%', :adsType, '%')))
              and (:platformName is null or lower(s.platformName) like lower(concat('%', :platformName, '%')))
              and (:campainName is null or lower(a.adsName) like lower(concat('%', :campainName, '%')))
              and (:adsOwner is null or lower(a.adsOwner) like lower(concat('%', :adsOwner, '%')))
            """)
    Page<ShiftLinkAud> searchAudits(String adsType, String platformName, String campainName, String adsOwner, Pageable pageable);
}
