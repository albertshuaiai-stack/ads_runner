package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Gmail-返利平台-前落地页链接归类规则 / Gmail-platform link classify rule
@Entity
@Table(name = "GMAIL_PLATFORM_LINK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GmailPlatformLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    // Gmail 账号 userName，对应 TOOL_EMAL / gmail account userName from TOOL_EMAL
    @Column(name = "USER_NAME", length = 32, nullable = false)
    private String userName;

    // 返利平台名 / rebate platform name
    @Column(name = "PLATFORM_NAME", length = 128, nullable = false)
    private String platformName;

    // 唯一归类 ID，对应 Gmail+平台（如 151919334），上传链接含此 ID 即归类
    // unique classify id bound to gmail+platform (e.g. 151919334), uploaded url containing it is classified
    @Column(name = "MATCH_ID", length = 128, nullable = false)
    private String matchId;

    @Column(name = "REMARKS", length = 1024)
    private String remarks;

    // RUNNING / DISABLED: 手动标记该邮箱在该平台是否还能用; DISABLED 统计算被封, 上传跳过
    // manual flag whether the account still works on the platform; DISABLED counts as banned and is skipped on upload
    @Column(name = "STATUS", length = 16, nullable = false)
    private String status = STATUS_RUNNING;

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_DISABLED = "DISABLED";

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
