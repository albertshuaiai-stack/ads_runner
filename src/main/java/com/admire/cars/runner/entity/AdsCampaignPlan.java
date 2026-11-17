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
 * 投前方案: 每单返现(算账) + 推广词(流量是否对) / pre-launch plan: commission (economics) and keywords (traffic)
 */
@Entity
@Table(name = "ADS_CAMPAIGN_PLAN")
@Getter
@Setter
public class AdsCampaignPlan {

    public static final String COMMISSION_FIXED = "FIXED";
    public static final String COMMISSION_PERCENT = "PERCENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_OWNER", nullable = false, length = 64)
    private String adsOwner;

    // 与 Google Ads 广告系列名 / Normal 任务名一致 / same as the Google Ads campaign and normal task name
    @Column(name = "CAMPAIGN_NAME", nullable = false, length = 128)
    private String campaignName;

    // 品牌名, 逗号分隔多个别名 / brand, comma separated aliases
    @Column(name = "BRAND_NAME", length = 128)
    private String brandName;

    @Column(name = "COUNTRY", length = 8)
    private String country;

    // FIXED: 每单固定返现 USD; PERCENT: 订单金额百分比 / fixed USD per order or percent of order value
    @Column(name = "COMMISSION_TYPE", length = 16)
    private String commissionType;

    @Column(name = "COMMISSION_VALUE", precision = 18, scale = 4)
    private BigDecimal commissionValue;

    @Column(name = "AVG_ORDER_VALUE", precision = 18, scale = 4)
    private BigDecimal avgOrderValue;

    // 预计佣金确认率 0~1 / expected approval rate 0..1
    @Column(name = "EXPECTED_APPROVAL_RATE", precision = 6, scale = 4)
    private BigDecimal expectedApprovalRate;

    // 预计单次点击花费 USD / expected cost per click in USD
    @Column(name = "EXPECTED_CPC", precision = 18, scale = 4)
    private BigDecimal expectedCpc;

    // 一行一个: [exact] / "phrase" / broad / one per line
    @Column(name = "KEYWORDS", columnDefinition = "TEXT")
    private String keywords;

    @Column(name = "NEGATIVE_KEYWORDS", columnDefinition = "TEXT")
    private String negativeKeywords;

    @Column(name = "NOTES", length = 1000)
    private String notes;

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
