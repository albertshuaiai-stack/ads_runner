package com.admire.cars.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "AFFILIATE_ADS_SYNC_CONFIG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AffiliateAdsSyncConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "AFFILIATE_NETWORK", nullable = false, length = 64)
    private String affiliateNetwork;

    @Column(name = "SYNC_NAME", nullable = false, length = 64)
    private String syncName;

    @Column(name = "URL", nullable = false, length = 1024)
    private String url;

    @Column(name = "METHOD", nullable = false, length = 64)
    private String method;

    @Column(name = "REQUEST_HEADERS", length = 1024)
    private String requestHeaders;

    @Column(name = "REQUEST_PAYLOAD", length = 1024)
    private String requestPayload;

    @Lob
    @Column(name = "RESPONSE_PAYLOAD", columnDefinition = "CLOB")
    private String responsePayload;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
