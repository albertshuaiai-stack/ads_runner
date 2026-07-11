package com.admire.cars.runner.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "TOOL_EMAL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ToolEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "USER_NAME", unique = true, length = 16)
    private String userName;

    @Column(name = "BIRTHDAY_DATE")
    private LocalDate birthdayDate;

    @Column(name = "EMAIL_ADDRESS", unique = true, length = 64)
    private String emailAddress;

    @JsonAlias("emaliPwd")
    @Column(name = "EMALI_PWD", length = 64)
    private String emailPwd;

    @Column(name = "PARENT_EMAIL", length = 64)
    private String parentEmail;

    @Column(name = "ADDRESS", length = 128)
    private String address;

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
