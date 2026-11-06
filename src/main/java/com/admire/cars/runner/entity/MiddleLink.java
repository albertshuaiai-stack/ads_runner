package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 中间链接存档：上传后按 Match ID 归类，再解析落地页归档 Matrix
// middle link archive: classified by match id on upload, resolved landing pages go to Matrix
@Entity
@Table(name = "MIDDLE_LINK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MiddleLink {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RESOLVING = "RESOLVING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "FULL_URL", length = 1024, nullable = false)
    private String fullUrl;

    // 品牌名，上传时必填 / brand name, required on upload
    @Column(name = "BRAND_NAME", length = 128, nullable = false)
    private String brandName;

    // 归类到的 Gmail userName，未归类为空 / classified gmail userName, null when unmatched
    @Column(name = "USER_NAME", length = 32)
    private String userName;

    // 归类到的返利平台 / classified rebate platform
    @Column(name = "PLATFORM_NAME", length = 128)
    private String platformName;

    @Column(name = "RESOLVE_STATUS", length = 16, nullable = false)
    private String resolveStatus = STATUS_PENDING;

    @Column(name = "RESOLVED_COUNT", nullable = false)
    private Long resolvedCount = 0L;

    @Column(name = "TARGET_COUNT", nullable = false)
    private Long targetCount = 24L;

    // 解析出的落地页，每行一个 / resolved landing pages, one per line
    @Column(name = "RESOLVED_URLS", columnDefinition = "TEXT")
    private String resolvedUrls;

    @Column(name = "LAST_ERROR", length = 1024)
    private String lastError;

    @Column(name = "ADS_OWNER", length = 32, nullable = false)
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
