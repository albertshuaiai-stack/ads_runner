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
@Table(name = "RAN_SITE_ID_POOL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RanSiteIdPool {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_BANNED = "BANNED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    // 池编码, 供 TRACK_CAMPAIGN 引用 / pool code referenced by TRACK_CAMPAIGN
    @Column(name = "POOL_CODE", nullable = false, length = 64)
    private String poolCode;

    @Column(name = "ADS_OWNER", nullable = false, length = 32)
    private String adsOwner;

    // 联盟平台, 如 CJ / Rakuten / Impact / affiliate network
    @Column(name = "PLATFORM", length = 64)
    private String platform;

    // 广告主, 如 Dell / PUMA / advertiser
    @Column(name = "ADVERTISER", length = 128)
    private String advertiser;

    @Column(name = "SITE_ID", nullable = false, length = 128)
    private String siteId;

    @Column(name = "WEIGHT")
    private Integer weight;

    @Column(name = "DAILY_CAP")
    private Long dailyCap;

    @Column(name = "USED_TODAY")
    private Long usedToday;

    @Column(name = "TOTAL_USED")
    private Long totalUsed;

    @Column(name = "STATUS", nullable = false, length = 16)
    private String status;

    @Column(name = "LAST_USED_AT")
    private LocalDateTime lastUsedAt;

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
            status = STATUS_ACTIVE;
        }
        if (weight == null) {
            weight = 1;
        }
        if (usedToday == null) {
            usedToday = 0L;
        }
        if (totalUsed == null) {
            totalUsed = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
