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
import java.time.LocalDateTime;

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

    @Column(name = "advertiser_shop_id", length = 64)
    private String advertiserShopId;

    @Column(name = "advertiser_shop_name", length = 512)
    private String advertiserShopName;

    @Column(name = "sign_id", length = 64)
    private String signId;

    @Column(name = "order_no", length = 128)
    private String orderNo;

    @Column(name = "order_time", length = 32)
    private String orderTime;

    @Column(name = "order_amount", precision = 19, scale = 2)
    private BigDecimal orderAmount;

    @Column(name = "user_commission_amount", precision = 19, scale = 2)
    private BigDecimal userCommissionAmount;

    @Column(name = "status", length = 64)
    private String status;

    @Column(name = "sub_id", length = 128)
    private String subId;

    @Column(name = "sub_id2", length = 128)
    private String subId2;

    @Column(name = "click_time", length = 32)
    private String clickTime;

    // 归因结果: 点击ID -> ShiftLink -> 后台上架的 normaltask / attribution result
    @Column(name = "CLICK_ID", length = 64)
    private String clickId;

    @Column(name = "NORMAL_ADS_ID")
    private Long normalAdsId;

    @Column(name = "SHIFT_LINK_ID")
    private Long shiftLinkId;

    @Column(name = "MATCH_STATUS", length = 16)
    private String matchStatus;

    @Column(name = "MATCH_RULE", length = 32)
    private String matchRule;

    @Column(name = "ATTRIBUTED_AT")
    private LocalDateTime attributedAt;
}
