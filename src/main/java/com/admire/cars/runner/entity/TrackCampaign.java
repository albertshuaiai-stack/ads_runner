package com.admire.cars.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TRACK_CAMPAIGN")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackCampaign {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_PAUSED = "PAUSED";

    public static final String ROTATE_WEIGHTED_RANDOM = "WEIGHTED_RANDOM";
    public static final String ROTATE_FIXED = "FIXED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    // 对外短码, 组成 /r/{slug} / public short code used as /r/{slug}
    @Column(name = "SLUG", nullable = false, length = 64)
    private String slug;

    @Column(name = "ADS_OWNER", nullable = false, length = 32)
    private String adsOwner;

    // 对应 Google Ads 广告系列名 / matching Google Ads campaign name
    @Column(name = "CAMPAIGN_NAME", length = 128)
    private String campaignName;

    // 联盟链接模板, 含 {siteId} 与 {clickId} 占位 / offer template with {siteId} and {clickId} placeholders
    @Column(name = "OFFER_URL_TEMPLATE", nullable = false, length = 1024)
    private String offerUrlTemplate;

    // 最终落地页, 用于一致性校验与异常兜底 / final landing page, used for consistency check and fallback
    @Column(name = "LANDING_PAGE_URL", length = 1024)
    private String landingPageUrl;

    // 落地域名白名单, 逗号分隔 / allowed final landing hosts, comma separated
    @Column(name = "ALLOWED_LANDING_HOSTS", length = 512)
    private String allowedLandingHosts;

    // 引用的 siteId 池编码 / pool code of site ids in use
    @Column(name = "POOL_CODE", length = 64)
    private String poolCode;

    // 分流策略 / rotation strategy: WEIGHTED_RANDOM or FIXED
    @Column(name = "ROTATE_STRATEGY", length = 32)
    private String rotateStrategy;

    @Column(name = "FIXED_SITE_ID", length = 128)
    private String fixedSiteId;

    @Column(name = "STATUS", nullable = false, length = 16)
    private String status;

    @Column(name = "REMARKS", length = 128)
    private String remarks;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = STATUS_RUNNING;
        }
        if (rotateStrategy == null || rotateStrategy.isBlank()) {
            rotateStrategy = ROTATE_WEIGHTED_RANDOM;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
