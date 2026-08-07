package com.admire.cars.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "AFFILIATE_AUTO_TASK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AffiliateAutoTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "AFFILIATE_NETWORK", nullable = false, length = 64)
    private String affiliateNetwork;

    @Column(name = "AUTO_TASK_TYPE", nullable = false, length = 64)
    private String autoTaskType;

    @Column(name = "REGION", nullable = false, length = 64)
    private String region;

    @Column(name = "TOTAL_COUNT")
    private Long totalCount;

    @Column(name = "SUCCESS_COUNT")
    private Long successCount;

    @Column(name = "FAILED_COUNT")
    private Long failedCount;

    @Column(name = "START_DATE")
    private LocalDateTime startDate;

    @Column(name = "END_DATE")
    private LocalDateTime endDate;

    @Column(name = "DURATION")
    private Long duration;

    @Column(name = "STATUS", nullable = false, length = 64)
    private String status;

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
