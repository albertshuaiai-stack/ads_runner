package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 手工录入的历史投放数据：Gmail + 平台 + 商家 + 是否被封，汇入统计与商家评级
// manually entered history: gmail + platform + merchant + banned flag, merged into stats and merchant rating
@Entity
@Table(name = "SHIFT_LINK_HISTORY", uniqueConstraints = @UniqueConstraint(
        name = "UK_SHIFT_LINK_HISTORY_GROUP",
        columnNames = {"ADS_OWNER", "USER_NAME", "PLATFORM_NAME", "ADS_NAME"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShiftLinkHistory {

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

    // 该 Gmail 在该平台的账号是否被封 / whether this gmail account was banned on the platform
    @Column(name = "BANNED", nullable = false)
    private Boolean banned = Boolean.FALSE;

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
