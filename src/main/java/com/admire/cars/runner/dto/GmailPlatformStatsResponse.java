package com.admire.cars.runner.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 统计响应：账号维度 + 商家评级 / stats response: account stats + merchant ratings
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GmailPlatformStatsResponse {
    private List<GmailPlatformStatDto> accounts;
    private List<MerchantRatingDto> merchants;
}
