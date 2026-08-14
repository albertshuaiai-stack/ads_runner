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

import java.math.BigDecimal;

@Entity
@Table(name = "ADS_NORMAL_POST_BACK")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdsNormalPostBack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ADS_OWNER", length = 64)
    private String adsOwner;

    @Column(name = "AFFILIATE_SITE", length = 64)
    private String affiliateSite;

    @Column(name = "ADVERTISER_SHOP_ID", length = 64)
    private String advertiserShopId;

    @Column(name = "ADVERTISER_SHOP_NAME", length = 512)
    private String advertiserShopName;

    @Column(name = "SIGN_ID", length = 64)
    private String signId;

    @Column(name = "ORDER_NO", length = 128)
    private String orderNo;

    @Column(name = "ORDER_TIME", length = 32)
    private String orderTime;

    @Column(name = "ORDER_AMOUNT", precision = 19, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "USER_COMMISSION_AMOUNT", precision = 19, scale = 2)
    private BigDecimal userCommissionAmount;

    @Column(name = "STATUS", length = 64)
    private String status;

    @Column(name = "SUB_ID", length = 128)
    private String subId;

    @Column(name = "SUB_ID2", length = 128)
    private String subId2;

    @Column(name = "CLICK_TIME", length = 32)
    private String clickTime;
}
