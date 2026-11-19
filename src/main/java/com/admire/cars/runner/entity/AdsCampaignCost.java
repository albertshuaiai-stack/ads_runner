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
import java.time.LocalDateTime;

/**
 * Google Ads 花费台账: 每个 campaign 一行累计花费, 不随 Normal 任务或广告删除而清零
 * Ad cost ledger: lifetime cost per Google Ads campaign, kept when the task or the campaign is deleted
 */
@Entity
@Table(name = "ADS_CAMPAIGN_COST")
@Getter
@Setter
public class AdsCampaignCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_OWNER", nullable = false, length = 64)
    private String adsOwner;

    // ID:<campaignId> / NAME:<name> / NAME:<name>@<millis>(已归档 / archived)
    @Column(name = "CAMPAIGN_KEY", nullable = false, length = 160)
    private String campaignKey;

    // Google Ads campaign id, 旧脚本不带时为空 / empty for old scripts
    @Column(name = "CAMPAIGN_ID", length = 32)
    private String campaignId;

    @Column(name = "CAMPAIGN_NAME", nullable = false, length = 128)
    private String campaignName;

    // ENABLED / PAUSED / REPLACED(旧脚本判定为重建前的旧 campaign / replaced by a recreated campaign)
    @Column(name = "CAMPAIGN_STATUS", length = 16)
    private String campaignStatus;

    // 账户币种的累计花费 / lifetime cost in account currency
    @Column(name = "LIFETIME_COST", nullable = false, precision = 18, scale = 4)
    private BigDecimal lifetimeCost;

    @Column(name = "COST_CURRENCY", length = 8)
    private String costCurrency;

    // 最近绑定的 Normal 任务, 任务删除后保留 / last bound normal task, kept after deletion
    @Column(name = "NORMAL_ADS_ID")
    private Long normalAdsId;

    @Column(name = "LAST_REPORT_TIME")
    private LocalDateTime lastReportTime;

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
