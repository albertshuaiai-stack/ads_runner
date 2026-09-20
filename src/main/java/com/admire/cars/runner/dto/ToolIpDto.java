package com.admire.cars.runner.dto;

import com.admire.cars.runner.entity.ToolIp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolIpDto {
    private Long id;
    private String ip;
    private LocalDate startDate;
    private LocalDate expireDate;
    private String remarks;
    private String adsOwner;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    public static ToolIpDto fromEntity(ToolIp e) {
        if (e == null) return null;
        return new ToolIpDto(
                e.getId(),
                e.getIp(),
                e.getStartDate() == null ? null : e.getStartDate().toLocalDate(),
                e.getExpireDate() == null ? null : e.getExpireDate().toLocalDate(),
                e.getRemarks(),
                e.getAdsOwner(),
                e.getCreateDate(),
                e.getUpdateDate()
        );
    }

    public ToolIp toEntity() {
        ToolIp e = new ToolIp();
        e.setId(this.id);
        e.setIp(this.ip);
        e.setStartDate(this.startDate == null ? null : this.startDate.atStartOfDay());
        e.setExpireDate(this.expireDate == null ? null : this.expireDate.atStartOfDay());
        e.setRemarks(this.remarks);
        e.setAdsOwner(this.adsOwner);
        e.setCreateDate(this.createDate);
        e.setUpdateDate(this.updateDate);
        return e;
    }
}
