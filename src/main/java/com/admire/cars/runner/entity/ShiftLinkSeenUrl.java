package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// 已统计过的落地页 URL 哈希，用于去重 / hashes of counted landing urls for deduplication
@Entity
@Table(name = "SHIFT_LINK_SEEN_URL", uniqueConstraints = @UniqueConstraint(
        name = "UK_SHIFT_LINK_SEEN_URL", columnNames = {"ADS_OWNER", "URL_HASH"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShiftLinkSeenUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_OWNER", length = 32, nullable = false)
    private String adsOwner;

    @Column(name = "URL_HASH", length = 64, nullable = false)
    private String urlHash;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @PrePersist
    protected void onCreate() {
        if (createDate == null) {
            createDate = LocalDateTime.now();
        }
    }
}
