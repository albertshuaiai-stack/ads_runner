package com.admire.cars.runner.entity;

import com.admire.cars.runner.constant.StatusConstant;
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
@Table(name = "AFFILIATE_ADS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AffiliateAds {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SITE_NAME", nullable = false, length = 128)
    private String siteName;

    @Column(name = "SITE_URL", length = 1024)
    private String siteUrl;

    @Column(name = "SITE_LOGO_URL", length = 1024)
    private String siteLogoUrl;

    @Column(name = "TRACKING_URL", length = 1024)
    private String trackingUrl;

    @Column(name = "REGION", length = 512)
    private String region;

    @Column(name = "MERCHANT_STATUS", length = 128)
    private String merchantStatus;

    @Column(name = "COMMISSIONS", length = 512)
    private String commissions;

    @Column(name = "ADV_CATAGORY", length = 64)
    private String advCatagory;

    @Column(name = "DEEPLINK", length = 64)
    private String deeplink;

    @Column(name = "AFFILIATE_NETWORK", nullable = false, length = 64)
    private String affiliateNetwork;

    @Column(name = "STATUS", nullable = false, length = 32)
    private String status;

    @Column(name = "ADS_OWNER", nullable = false, length = 32)
    private String adsOwner;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    protected void onCreate() {
        if (createDate == null) {
            createDate = LocalDateTime.now();
        }
        if (status == null || status.isBlank()) {
            status = StatusConstant.TO_BE_TEST;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
