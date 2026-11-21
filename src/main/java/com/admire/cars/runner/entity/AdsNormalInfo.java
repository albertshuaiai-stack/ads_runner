package com.admire.cars.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ADS_NORMAL_INFO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdsNormalInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CAMPAIN_NAME", nullable = false, length = 32)
    private String campainName;

    @Column(name = "CAMPAIN_COUNTRY", nullable = false, length = 8)
    private String campainCountry;

    @Column(name = "PLATFORM_NAME", nullable = false, length = 32)
    private String platformName;

    @Column(name = "AFFILITE_URL", length = 128)
    private String affiliteUrl;

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

    @Column(name = "SUCCESS_COUNT")
    private Long successCount;

    @Column(name = "FAILED_COUNT")
    private Long failedCount;

    @Column(name = "LAST_SUCCESS_DATE")
    private LocalDateTime lastSuccessDate;

    // 成功/失败计数所属日期(服务器本地自然日) / server-local day the success/failed counters belong to
    @Column(name = "COUNT_DATE", length = 10)
    private String countDate;

    // 每日额度所属日期 / calendar day the allowance belongs to
    @Column(name = "QUOTA_DATE", length = 10)
    private String quotaDate;

    // 最近一次点击上报的累计点击数 / lifetime clicks from the latest report
    @Column(name = "LAST_REPORT_CLICKS")
    private Long lastReportClicks;

    // 当天累计可生成条数: base + 每次点击增量 * multiplier / links allowed today
    @Column(name = "DAILY_ALLOWANCE")
    private Long dailyAllowance;

    // 最近一次上报的 Google Ads 累计花费(账户币种) / lifetime ad cost from the latest report, in account currency
    @Column(name = "LAST_REPORT_COST", precision = 18, scale = 4)
    private BigDecimal lastReportCost;

    // Google Ads 账户币种 / Google Ads account currency code
    @Column(name = "COST_CURRENCY", length = 8)
    private String costCurrency;

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

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "RUNNING";
        }
    }

    @jakarta.persistence.PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
