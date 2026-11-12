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

import java.time.LocalDateTime;

/**
 * 点击ID台账: 每生成一条 ShiftLink 记录一个唯一 subid, 用于把转化回传归因到 normaltask
 * Click id ledger: one unique subid per generated shift link, used to attribute postbacks to a normal task
 */
@Entity
@Table(name = "ADS_CLICK_TRACK")
@Getter
@Setter
public class AdsClickTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    // 写进联盟链接 {subid} 的 UUID / UUID written into the affiliate {subid} placeholder
    @Column(name = "CLICK_ID", nullable = false, length = 64, unique = true)
    private String clickId;

    // 后台上架的 normaltask / listed normal task
    @Column(name = "NORMAL_ADS_ID")
    private Long normalAdsId;

    @Column(name = "SHIFT_LINK_ID")
    private Long shiftLinkId;

    @Column(name = "CAMPAIN_NAME", length = 64)
    private String campainName;

    @Column(name = "PLATFORM_NAME", length = 64)
    private String platformName;

    @Column(name = "ADS_OWNER", nullable = false, length = 64)
    private String adsOwner;

    @Column(name = "AFFILIATE_URL", length = 1024)
    private String affiliateUrl;

    @Column(name = "FULL_URL", length = 1024)
    private String fullUrl;

    @Column(name = "LANDING_PAGE_URL", length = 1024)
    private String landingPageUrl;

    // 真实点击数(仅启用 /api/click 跳转时累加) / real hits counted only when /api/click is used
    @Column(name = "CLICK_COUNT")
    private Long clickCount;

    @Column(name = "LAST_CLICK_TIME")
    private LocalDateTime lastClickTime;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        if (clickCount == null) {
            clickCount = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
