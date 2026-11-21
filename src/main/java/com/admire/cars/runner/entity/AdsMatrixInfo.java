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
import jakarta.persistence.Transient;
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

    @Column(name = "CAMPAIN_NAME", nullable = false, length = 64)
    private String campainName;

    @Column(name = "CAMPAIN_COUNTRY", nullable = false, length = 8)
    private String campainCountry;

    @Column(name = "LANDING_PAGE_URL", length = 1024)
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

    @Column(name = "SUCCESS_COUNT")
    private Long successCount;

    @Column(name = "FAILED_COUNT")
    private Long failedCount;

    @Column(name = "LAST_SUCCESS_DATE")
    private LocalDateTime lastSuccessDate;

    // 成功/失败计数所属日期(服务器本地自然日) / server-local day the success/failed counters belong to
    @Column(name = "COUNT_DATE", length = 10)
    private String countDate;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @Transient
    private LocalDateTime lastExecuteTime;

    @Transient
    private LocalDateTime nextExecuteTime;

    // 当前可被 Google Ads 拉取使用的链数, 仅展示 / links Google Ads can still use, display only
    @Transient
    private Long availableCount;

    @OneToMany(mappedBy = "matrixInfo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<AdsMatrixAffiliateInfo> affiliateInfos = new ArrayList<>();

    // 跨天先把计数归零 / reset the counters when the day changes
    public void rollCountDate(String today) {
        if (!today.equals(countDate)) {
            successCount = 0L;
            failedCount = 0L;
            countDate = today;
        }
    }

    public Long getSuccessCount() {
        if (successCount == null) {
            successCount = 0L;
        }
        return successCount;
    }

    public Long getFailedCount() {
        if (failedCount == null) {
            failedCount = 0L;
        }
        return failedCount;
    }

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
