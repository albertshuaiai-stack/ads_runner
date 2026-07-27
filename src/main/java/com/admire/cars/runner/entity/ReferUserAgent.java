package com.admire.cars.runner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "REFER_USER_AGENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReferUserAgent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "DEVICE", nullable = false, length = 16)
    private String device;

    @Column(name = "USER_AGENT", nullable = false, length = 512)
    private String userAgent;
}
