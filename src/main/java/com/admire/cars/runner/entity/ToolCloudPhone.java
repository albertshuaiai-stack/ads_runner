package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TOOL_CLOUD_PHONE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ToolCloudPhone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "COUNTRY_CD", length = 32)
    private String countryCd;

    @Column(name = "PHONE_NUMBER", length = 64, nullable = false)
    private String phoneNumber;

    @Column(name = "START_DATE")
    private LocalDateTime startDate;

    @Column(name = "EXPIRE_DATE")
    private LocalDateTime expireDate;

    @Column(name = "REMARKS", length = 128)
    private String remarks;

    @Column(name = "ADS_OWNER", length = 64)
    private String adsOwner;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    protected void onCreate() {
        if (createDate == null) createDate = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
