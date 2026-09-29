package com.admire.cars.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TRACK_CLICK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    // 点击唯一标识, 透传给联盟用于回传归因 / unique click id passed to network for attribution
    @Column(name = "CLICK_ID", nullable = false, length = 64)
    private String clickId;

    @Column(name = "SLUG", length = 64)
    private String slug;

    @Column(name = "ADS_OWNER", length = 32)
    private String adsOwner;

    @Column(name = "CAMPAIGN_NAME", length = 128)
    private String campaignName;

    @Column(name = "SITE_ID", length = 128)
    private String siteId;

    @Column(name = "PLATFORM", length = 64)
    private String platform;

    @Column(name = "ADVERTISER", length = 128)
    private String advertiser;

    // 保留 Google 点击标识, 仅用于自身归因分析 / keep gclid for own attribution analysis
    @Column(name = "GCLID", length = 512)
    private String gclid;

    @Column(name = "TARGET_URL", length = 1024)
    private String targetUrl;

    @Column(name = "LANDING_PAGE_URL", length = 1024)
    private String landingPageUrl;

    @Column(name = "IP", length = 64)
    private String ip;

    @Column(name = "USER_AGENT", length = 512)
    private String userAgent;

    @Column(name = "REFERER", length = 1024)
    private String referer;

    @Column(name = "DEVICE", length = 32)
    private String device;

    @Column(name = "COUNTRY", length = 16)
    private String country;

    @Column(name = "CLICK_TIME")
    private LocalDateTime clickTime;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        if (clickTime == null) {
            clickTime = createDate;
        }
    }
}
