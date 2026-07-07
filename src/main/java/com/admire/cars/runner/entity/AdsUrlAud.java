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

    @Column(name = "CAPMAIN_NAME", nullable = false, length = 64)
    private String capMainName;

    @Column(name = "FULL_URL", nullable = false, length = 512)
    private String fullUrl;

    @Column(name = "LANDING_URL", length = 256)
    private String landingUrl;

    @Column(name = "URL_SUFFIX", length = 512)
    private String urlSuffix;

    @Column(name = "PLATFORM", length = 64)
    private String platform;

    @Column(name = "REMARK", length = 64)
    private String remark;

    @Column(name = "CAMPAIN_OWNER", nullable = false)
    private Long campaignOwner;

    @Column(name = "SEQ_NUMBER")
    private Long seqNumber;

    @Column(name = "DISPLAY_TIMES")
    private Long displayTimes;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
