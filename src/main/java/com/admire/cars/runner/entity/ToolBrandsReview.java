package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "TOOL_BRANDS_REVIEW")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ToolBrandsReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "BRAND", length = 128, nullable = false)
    private String brand;

    @Column(name = "SCORE")
    private Long score;

    @Column(name = "REMARKS", length = 1024)
    private String remarks;

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
