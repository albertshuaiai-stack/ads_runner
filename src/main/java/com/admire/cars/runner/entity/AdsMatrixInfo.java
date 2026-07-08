package com.admire.cars.runner.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ADS_MATRIX_INFO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdsMatrixInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CAMPAIN_NAME", nullable = false, length = 32)
    private String campainName;

    @Column(name = "CAMPAIN_COUNTRY", nullable = false, length = 8)
    private String campainCountry;

    @Column(name = "LANDING_PAGE_URL", length = 128)
    private String landingPageUrl;

    @Column(name = "DYNAMIC_PROXY_INFO", length = 256)
    private String dynamicProxyInfo;

    @Column(name = "DYNAMIC_PROXY_INFO_BACKUP", length = 256)
    private String dynamicProxyInfoBackup;

    @Column(name = "INTERVAL_TIME")
    private Long intervalTime;

    @Column(name = "STATUS", nullable = false, length = 16)
    private String status;

    @Column(name = "ADS_OWNER", nullable = false, length = 32)
    private String adsOwner;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @OneToMany(mappedBy = "matrixInfo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<AdsMatrixAffiliateInfo> affiliateInfos = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "RUNNING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
