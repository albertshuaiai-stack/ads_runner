package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ADS_USER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USER_NAME", nullable = false, unique = true, length = 32)
    private String userName;

    @Column(name = "USER_EMAIL", nullable = false, unique = true, length = 32)
    private String userEmail;

    @Column(name = "USER_PHONE_NUMBER", nullable = false, unique = true, length = 16)
    private String userPhoneNumber;

    @Column(name = "USER_ROLE", length = 128)
    private String userRole;

    @Column(name = "USER_PASSWORD", nullable = false, length = 128)
    private String userPassword;

    @Column(name = "EXPIRE_DATE")
    private LocalDateTime expireDate;

    @Column(name = "NORMAL_ADS_NUMBER")
    private Long normalAdsNumber;

    @Column(name = "MATRIX_ADS_NUMBER")
    private Long matrixAdsNumber;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status;

    @Column(name = "CREATE_DATE", nullable = false)
    private LocalDateTime createDate;

    @Column(name = "UPDATE_DATE")
    private LocalDateTime updateDate;

    @PrePersist
    protected void onCreate() {
        createDate = LocalDateTime.now();
        status = "ENABLED";
        if (userRole == null) {
            userRole = "USER";
        }
        if (normalAdsNumber == null) {
            normalAdsNumber = 0L;
        }
        if (matrixAdsNumber == null) {
            matrixAdsNumber = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateDate = LocalDateTime.now();
    }
}
