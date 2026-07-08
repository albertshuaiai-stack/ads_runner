package com.admire.cars.runner.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ADS_MATRIX_AFFILIATE_INFO")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdsMatrixAffiliateInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CAMPAIN_ID", nullable = false)
    @JsonBackReference
    private AdsMatrixInfo matrixInfo;

    @Column(name = "PLATFORM_NAME", nullable = false, length = 32)
    private String platformName;

    @Column(name = "AFFILITE_URL", length = 128)
    private String affiliteUrl;

    @Column(name = "DISPLAY_NUMBER")
    private Long displayNumber;

    @Column(name = "REMARKS", length = 64)
    private String remarks;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        if (displayNumber == null) {
            displayNumber = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
