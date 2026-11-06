package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Shift Link 历史聚合：30 天清理前归档计数，统计接口合并此表计算
// historical shift link aggregates archived before the 30-day cleanup; stats merge this table
@Entity
@Table(name = "SHIFT_LINK_STAT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShiftLinkStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_OWNER", length = 32, nullable = false)
    private String adsOwner;

    @Column(name = "USER_NAME", length = 32, nullable = false)
    private String userName;

    @Column(name = "PLATFORM_NAME", length = 32, nullable = false)
    private String platformName;

    @Column(name = "ADS_NAME", length = 32, nullable = false)
    private String adsName;

    @Column(name = "LINK_COUNT", nullable = false)
    private Long linkCount = 0L;

    // 商家样例链接，用于站点根外链 / sample link kept for the merchant site root
    @Column(name = "SAMPLE_URL", length = 1024)
    private String sampleUrl;

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
