package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ADS_URL_AUD")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdsUrlAud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUD_ID")
    private Long audId;

    @Column(name = "ID", nullable = false)
    private Long id;

    @Column(name = "CAPMAIN_NAME", nullable = false, length = 32)
    private String capMainName;

    @Column(name = "CAMPAIN_NAME", length = 32)
    private String campainName;

    @Column(name = "CAMPAIN_COUNTRY", length = 8)
    private String campainCountry;

    @Column(name = "FULL_URL", nullable = false, length = 512)
    private String fullUrl;

    @Column(name = "LANDING_URL", length = 128)
    private String landingUrl;

    @Column(name = "LANDING_PAGE_URL", length = 128)
    private String landingPageUrl;

    @Column(name = "URL_SUFFIX", length = 256)
    private String urlSuffix;

    @Column(name = "PLATFORM", length = 32)
    private String platform;

    @Column(name = "PLATFORM_NAME", length = 32)
    private String platformName;

    @Column(name = "REMARK", length = 64)
    private String remark;

    @Column(name = "REMARKS", length = 64)
    private String remarks;

    @Column(name = "CAMPAIN_OWNER", nullable = false)
    private Long campaignOwner;

    @Column(name = "ADS_OWNER", length = 32)
    private String adsOwner;

    @Column(name = "ADS_TYPE", length = 16)
    private String adsType;

    @Column(name = "CAMPAIN_ID")
    private Long campainId;

    @Column(name = "SEQ_NUMBER")
    private Long seqNumber;

    @Column(name = "DISPLAY_NUMBER")
    private Long displayNumber;

    @Column(name = "DISPLAY_TIMES")
    private Long displayTimes;

    @Column(name = "STATUS", length = 16)
    private String status;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @Column(name = "VERSION")
    private Long version;

    @Column(name = "REVERSION", nullable = false)
    private Long reversion;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        displayTimes = 0L;
        if (displayNumber == null) {
            displayNumber = 0L;
        }
        if (status == null || status.isBlank()) {
            status = "RUNNING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
