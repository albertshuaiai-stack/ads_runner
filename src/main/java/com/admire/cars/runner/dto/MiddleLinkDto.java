package com.admire.cars.runner.dto;

import com.admire.cars.runner.entity.MiddleLink;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiddleLinkDto {
    private Long id;
    private String fullUrl;
    private String brandName;
    private String matchId;
    private String userName;
    private String platformName;
    private String resolveStatus;
    private Long resolvedCount;
    private Long targetCount;
    private String resolvedUrls;
    private String lastError;
    private String adsOwner;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    public static MiddleLinkDto fromEntity(MiddleLink e) {
        if (e == null) return null;
        return new MiddleLinkDto(
                e.getId(),
                e.getFullUrl(),
                e.getBrandName(),
                e.getMatchId(),
                e.getUserName(),
                e.getPlatformName(),
                e.getResolveStatus(),
                e.getResolvedCount(),
                e.getTargetCount(),
                e.getResolvedUrls(),
                e.getLastError(),
                e.getAdsOwner(),
                e.getCreateDate(),
                e.getUpdateDate()
        );
    }
}
