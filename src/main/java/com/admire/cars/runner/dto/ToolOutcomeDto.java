package com.admire.cars.runner.dto;

import com.admire.cars.runner.entity.ToolOutcome;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolOutcomeDto {
    private Long id;
    private String outcomeType;
    private BigDecimal outcomeAmount;
    private String currency;
    private LocalDate payDate;
    private String remarks;
    private String adsOwner;
    private String adsAccount;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    public static ToolOutcomeDto fromEntity(ToolOutcome e) {
        if (e == null) return null;
        return new ToolOutcomeDto(
                e.getId(),
                e.getOutcomeType(),
                e.getOutcomeAmount(),
                e.getCurrency(),
                e.getPayDate(),
                e.getRemarks(),
                e.getAdsOwner(),
                e.getAdsAccount(),
                e.getCreateDate(),
                e.getUpdateDate()
        );
    }

    public ToolOutcome toEntity() {
        ToolOutcome e = new ToolOutcome();
        e.setId(this.id);
        e.setOutcomeType(this.outcomeType);
        e.setOutcomeAmount(this.outcomeAmount);
        e.setCurrency(this.currency);
        e.setPayDate(this.payDate);
        e.setRemarks(this.remarks);
        e.setAdsOwner(this.adsOwner);
        e.setAdsAccount(this.adsAccount);
        e.setCreateDate(this.createDate);
        e.setUpdateDate(this.updateDate);
        return e;
    }
}
