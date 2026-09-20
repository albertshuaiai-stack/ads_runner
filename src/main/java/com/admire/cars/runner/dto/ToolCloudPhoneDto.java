package com.admire.cars.runner.dto;

import com.admire.cars.runner.entity.ToolCloudPhone;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCloudPhoneDto {
    private Long id;
    private String countryCd;
    private String phoneNumber;
    private LocalDate startDate;
    private LocalDate expireDate;
    private String remarks;
    private String adsOwner;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    public static ToolCloudPhoneDto fromEntity(ToolCloudPhone e) {
        if (e == null) return null;
        return new ToolCloudPhoneDto(
                e.getId(),
                e.getCountryCd(),
                e.getPhoneNumber(),
                e.getStartDate() == null ? null : e.getStartDate().toLocalDate(),
                e.getExpireDate() == null ? null : e.getExpireDate().toLocalDate(),
                e.getRemarks(),
                e.getAdsOwner(),
                e.getCreateDate(),
                e.getUpdateDate()
        );
    }

    public ToolCloudPhone toEntity() {
        ToolCloudPhone e = new ToolCloudPhone();
        e.setId(this.id);
        e.setCountryCd(this.countryCd);
        e.setPhoneNumber(this.phoneNumber);
        e.setStartDate(this.startDate == null ? null : this.startDate.atStartOfDay());
        e.setExpireDate(this.expireDate == null ? null : this.expireDate.atStartOfDay());
        e.setRemarks(this.remarks);
        e.setAdsOwner(this.adsOwner);
        e.setCreateDate(this.createDate);
        e.setUpdateDate(this.updateDate);
        return e;
    }
}
