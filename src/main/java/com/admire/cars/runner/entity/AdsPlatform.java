package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ADS_PLATFORM")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdsPlatform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "PLATFORM_NAME", nullable = false, unique = true, length = 32)
    private String platformName;

    @Column(name = "PAYMENT_METHOD", length = 64)
    private String paymentMethod;

    @Column(name = "REMARKS", length = 256)
    private String remarks;
}
