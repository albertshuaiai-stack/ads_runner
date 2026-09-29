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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TRACK_CONVERSION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackConversion {

    public static final String ATTRIBUTED_YES = "YES";
    public static final String ATTRIBUTED_NO = "NO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CLICK_ID", length = 64)
    private String clickId;

    @Column(name = "ORDER_NO", length = 128)
    private String orderNo;

    @Column(name = "ADS_OWNER", length = 32)
    private String adsOwner;

    @Column(name = "SLUG", length = 64)
    private String slug;

    @Column(name = "CAMPAIGN_NAME", length = 128)
    private String campaignName;

    @Column(name = "SITE_ID", length = 128)
    private String siteId;

    @Column(name = "PLATFORM", length = 64)
    private String platform;

    @Column(name = "ADVERTISER", length = 128)
    private String advertiser;

    @Column(name = "ORDER_AMOUNT", precision = 19, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "COMMISSION_AMOUNT", precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "STATUS", length = 64)
    private String status;

    @Column(name = "ATTRIBUTED", length = 16)
    private String attributed;

    @Column(name = "CONVERSION_TIME", length = 32)
    private String conversionTime;

    @Column(name = "RAW_PARAMS", length = 2048)
    private String rawParams;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        if (attributed == null || attributed.isBlank()) {
            attributed = ATTRIBUTED_NO;
        }
    }
}
