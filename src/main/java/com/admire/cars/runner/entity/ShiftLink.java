package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "SHIFT_LINK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShiftLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_TYPE", length = 16)
    private String adsType;

    @Column(name = "ADS_ID")
    private Long adsId;

    @Column(name = "PLATFORM_NAME", nullable = false, length = 32)
    private String platformName;

    @Column(name = "ADS_NAME", nullable = false, length = 32)
    private String adsName;

    @Column(name = "LANDING_PAGE_URL", length = 128)
    private String landingPageUrl;

    @Column(name = "FULL_URL", nullable = false, length = 512)
    private String fullUrl;

    @Column(name = "URL_SUFFIX", length = 256)
    private String urlSuffix;

    @Column(name = "DISPLAY_NUMBER")
    private Long displayNumber;

    @Column(name = "DISPLAY_TIMES")
    private Long displayTimes;

    @Column(name = "SEQ_NUMBER")
    private Long seqNumber;

    @Column(name = "REMARKS", length = 64)
    private String remarks;

    @Column(name = "STATUS", nullable = false, length = 16)
    private String status;

    @Column(name = "ADS_OWNER", nullable = false, length = 32)
    private String adsOwner;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        if (displayNumber == null) {
            displayNumber = 0L;
        }
        if (displayTimes == null) {
            displayTimes = 0L;
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
