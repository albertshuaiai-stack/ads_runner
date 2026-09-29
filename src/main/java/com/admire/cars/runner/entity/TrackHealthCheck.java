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
@Table(name = "TRACK_HEALTH_CHECK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackHealthCheck {

    public static final String YES = "YES";
    public static final String NO = "NO";
    public static final String NA = "NA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SLUG", length = 64)
    private String slug;

    @Column(name = "ADS_OWNER", length = 32)
    private String adsOwner;

    @Column(name = "CAMPAIGN_NAME", length = 128)
    private String campaignName;

    @Column(name = "FIRST_STATUS")
    private Integer firstStatus;

    @Column(name = "HOP_COUNT")
    private Integer hopCount;

    @Column(name = "TARGET_URL", length = 1024)
    private String targetUrl;

    @Column(name = "FINAL_URL", length = 1024)
    private String finalUrl;

    @Column(name = "FINAL_HOST", length = 256)
    private String finalHost;

    @Column(name = "BOT_FINAL_URL", length = 1024)
    private String botFinalUrl;

    @Column(name = "LANDING_OK", length = 8)
    private String landingOk;

    @Column(name = "AUTO_REDIRECT", length = 8)
    private String autoRedirect;

    @Column(name = "CLOAK_SUSPECTED", length = 8)
    private String cloakSuspected;

    @Column(name = "GCLID_PASSED", length = 8)
    private String gclidPassed;

    @Column(name = "PASSED", length = 8)
    private String passed;

    @Column(name = "MESSAGE", length = 512)
    private String message;

    @Column(name = "RAW_CHAIN", length = 2048)
    private String rawChain;

    @Column(name = "CHECK_TIME")
    private LocalDateTime checkTime;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        if (checkTime == null) {
            checkTime = createDate;
        }
        if (passed == null) {
            passed = NO;
        }
    }
}
