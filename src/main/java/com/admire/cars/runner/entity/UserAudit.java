package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ADS_USER_AUD")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUD_ID")
    private Long audId;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "OPERATION", nullable = false, length = 50)
    private String operation;

    @Column(name = "OPERATION_DATE", nullable = false)
    private LocalDateTime operationDate;

    @Column(name = "OLD_VALUE", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "NEW_VALUE", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "OPERATOR", length = 100)
    private String operator;

    @PrePersist
    protected void onCreate() {
        operationDate = LocalDateTime.now();
    }
}
