package com.admire.cars.runner.dto;

import com.admire.cars.runner.entity.GmailPlatformLink;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GmailPlatformLinkDto {
    private Long id;
    private String userName;
    private String platformName;
    private String matchId;
    private String remarks;
    private String adsOwner;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    public static GmailPlatformLinkDto fromEntity(GmailPlatformLink e) {
        if (e == null) return null;
        return new GmailPlatformLinkDto(
                e.getId(),
                e.getUserName(),
                e.getPlatformName(),
                e.getMatchId(),
                e.getRemarks(),
                e.getAdsOwner(),
                e.getCreateDate(),
                e.getUpdateDate()
        );
    }

    public GmailPlatformLink toEntity() {
        GmailPlatformLink e = new GmailPlatformLink();
        e.setId(this.id);
        e.setUserName(this.userName);
        e.setPlatformName(this.platformName);
        e.setMatchId(this.matchId);
        e.setRemarks(this.remarks);
        e.setAdsOwner(this.adsOwner);
        e.setCreateDate(this.createDate);
        e.setUpdateDate(this.updateDate);
        return e;
    }
}
