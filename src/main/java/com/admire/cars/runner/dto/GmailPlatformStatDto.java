package com.admire.cars.runner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Gmail -> 返利平台 -> 商家 统计 / gmail -> platform -> merchant stats
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GmailPlatformStatDto {
    private String userName;
    private String platformName;
    // 对应返利账号状态（TOOL_ACCOUNT.STATUS：RUNNING/LOCKED），无记录为空 / cash back account status, null when no account row
    private String accountStatus;
    private long linkCount;
    private long merchantCount;
    private List<MerchantStat> merchants;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantStat {
        private String adsName;
        private long linkCount;
        // 商家站点首页，用于外链 / merchant site root for external link
        private String merchantUrl;
    }
}
