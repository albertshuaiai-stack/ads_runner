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
@Table(name = "ADS_TASK_LOG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdsTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_NAME", length = 128)
    private String adsName;

    @Column(name = "ADS_TYPE", length = 32)
    private String adsType;

    @Column(name = "PLATFORM_NAME", length = 64)
    private String platformName;

    @Column(name = "DEVICE", length = 64)
    private String device;

    @Column(name = "USER_AGENT", length = 512)
    private String userAgent;

    @Column(name = "IP", length = 64)
    private String ip;

    @Column(name = "COUNTRY_CODE", length = 16)
    private String countryCode;

    @Column(name = "SEQUENCE")
    private Long sequence;

    @Column(name = "REQUEST_URL", length = 1024)
    private String requestUrl;

    @Column(name = "RESPONSE_URL", length = 1024)
    private String responseUrl;

    @Column(name = "STATUS_CODE", length = 16)
    private String statusCode;

    @Column(name = "DURATION_MILLIS", length = 16)
    private String durationMillis;

    @Column(name = "LOCATION", length = 1024)
    private String location;

    @Column(name = "SUCCESS")
    private Boolean success;

    @Column(name = "ERR_MSG", length = 256)
    private String errMsg;

    @Column(name = "ADS_OWNER", length = 32)
    private String adsOwner;

    @Column(name = "CREATE_DATE")
    private LocalDateTime createDate;

    @PrePersist
    protected void onCreate() {
        if (createDate == null) {
            createDate = LocalDateTime.now();
        }
    }
}
