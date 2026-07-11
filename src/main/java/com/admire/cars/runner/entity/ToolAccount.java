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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "TOOL_ACCOUNT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ToolAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "EMAIL_ADDRESS", length = 64)
    private String emailAddress;

    @Column(name = "USER_NAME", length = 16)
    private String userName;

    @Column(name = "PLATFORM_NAME", length = 32)
    private String platformName;

    @Column(name = "PAYMENT_STATUS", length = 32)
    private String paymentStatus;

    @Column(name = "STATUS", length = 32)
    private String status;

    @Column(name = "REGISTER_DATE")
    private LocalDate registerDate;

    @Column(name = "BALANCE", precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "CURRENCY", length = 32)
    private String currency;

    @Column(name = "REMARKS", length = 64)
    private String remarks;

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
