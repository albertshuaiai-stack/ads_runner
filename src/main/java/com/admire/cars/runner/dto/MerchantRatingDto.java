package com.admire.cars.runner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 商家投放评级：按推广账号的封禁分布打分 / merchant rating by ban distribution of promoting accounts
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRatingDto {
    private String adsName;
    private long linkCount;
    private long totalAccounts;
    private long bannedAccounts;
    private long activeAccounts;
    // SAFE / CAUTION / AVOID / UNKNOWN
    private String rating;
    // 商家站点首页，用于外链 / merchant site root for external link
    private String merchantUrl;
}
