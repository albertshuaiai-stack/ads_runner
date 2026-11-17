package com.admire.cars.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Google Ads 按天 x 网络类型的展示/点击/花费, 由脚本覆盖写入最近几天
 * Google Ads impressions/clicks/cost per day and network type, the script overwrites the recent days
 */
@Entity
@Table(name = "ADS_DAILY_STAT")
@Getter
@Setter
public class AdsDailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_OWNER", nullable = false, length = 64)
    private String adsOwner;

    @Column(name = "CAMPAIGN_NAME", nullable = false, length = 128)
    private String campaignName;

    // 账户时区的日期 / date in the Google Ads account time zone
    @Column(name = "STAT_DATE", nullable = false)
    private LocalDate statDate;

    // SEARCH / SEARCH_PARTNERS / CONTENT / YOUTUBE_* / MIXED ...
    @Column(name = "NETWORK", nullable = false, length = 32)
    private String network;

    @Column(name = "IMPRESSIONS")
    private Long impressions;

    @Column(name = "CLICKS")
    private Long clicks;

    // 账户币种 / account currency
    @Column(name = "COST", precision = 18, scale = 4)
    private BigDecimal cost;

    @Column(name = "CURRENCY", length = 8)
    private String currency;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
