package com.admire.cars.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "SHIFT_LINK_LOG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShiftLinkLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_TYPE", nullable = false, length = 16)
    private String adsType;

    @Column(name = "PLATFORM_NAME", nullable = false, length = 32)
    private String platformName;

    @Column(name = "ADS_NAME", nullable = false, length = 32)
    private String adsName;

    @Column(name = "FULL_URL", nullable = false, length = 512)
    private String fullUrl;

    @Column(name = "DISPLAY_TIMES", nullable = false)
    private Long displayTimes;

    @Column(name = "REMARKS", length = 64)
    private String remarks;

    @Column(name = "ADS_OWNER", nullable = false, length = 32)
    private String adsOwner;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @PrePersist
    protected void onCreate() {
        if (createDate == null) {
            createDate = LocalDateTime.now();
        }
        if (displayTimes == null) {
            displayTimes = 0L;
        }
    }
}
