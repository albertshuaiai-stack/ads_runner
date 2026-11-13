package com.admire.cars.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 近 7 天搜索词快照, 每次上报整体替换当天快照 / last-7-day search terms, each report replaces the day's snapshot
 */
@Entity
@Table(name = "ADS_SEARCH_TERM_STAT")
@Getter
@Setter
public class AdsSearchTermStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_OWNER", nullable = false, length = 64)
    private String adsOwner;

    @Column(name = "CAMPAIGN_NAME", nullable = false, length = 128)
    private String campaignName;

    @Column(name = "SNAPSHOT_DATE", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "SEARCH_TERM", nullable = false, length = 255)
    private String searchTerm;

    @Column(name = "IMPRESSIONS")
    private Long impressions;

    @Column(name = "CLICKS")
    private Long clicks;

    // 账户币种 / account currency
    @Column(name = "COST", precision = 18, scale = 4)
    private BigDecimal cost;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
    }
}
