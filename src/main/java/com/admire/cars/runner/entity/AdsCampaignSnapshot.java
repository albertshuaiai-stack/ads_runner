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
 * 广告系列设置与近 7 天展示份额快照 / campaign settings and last-7-day impression share snapshot
 */
@Entity
@Table(name = "ADS_CAMPAIGN_SNAPSHOT")
@Getter
@Setter
public class AdsCampaignSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_OWNER", nullable = false, length = 64)
    private String adsOwner;

    @Column(name = "CAMPAIGN_NAME", nullable = false, length = 128)
    private String campaignName;

    @Column(name = "CURRENCY", length = 8)
    private String currency;

    @Column(name = "GOOGLE_SEARCH")
    private Boolean googleSearch;

    @Column(name = "SEARCH_PARTNERS")
    private Boolean searchPartners;

    @Column(name = "DISPLAY_NETWORK")
    private Boolean displayNetwork;

    // PRESENCE / PRESENCE_OR_INTEREST
    @Column(name = "GEO_TARGET_TYPE", length = 32)
    private String geoTargetType;

    // MANUAL_CPC / TARGET_SPEND(Maximize clicks) / MAXIMIZE_CONVERSIONS ...
    @Column(name = "BIDDING_STRATEGY", length = 48)
    private String biddingStrategy;

    // 账户币种 / account currency
    @Column(name = "DAILY_BUDGET", precision = 18, scale = 4)
    private BigDecimal dailyBudget;

    @Column(name = "CPC_CEILING", precision = 18, scale = 4)
    private BigDecimal cpcCeiling;

    // 0~1
    @Column(name = "SEARCH_IMPRESSION_SHARE", precision = 8, scale = 4)
    private BigDecimal searchImpressionShare;

    @Column(name = "BUDGET_LOST_SHARE", precision = 8, scale = 4)
    private BigDecimal budgetLostShare;

    @Column(name = "RANK_LOST_SHARE", precision = 8, scale = 4)
    private BigDecimal rankLostShare;

    @Column(name = "REPORT_DATE")
    private LocalDate reportDate;

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
