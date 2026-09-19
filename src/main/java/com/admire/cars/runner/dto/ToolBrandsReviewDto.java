package com.admire.cars.runner.dto;

import com.admire.cars.runner.entity.ToolBrandsReview;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolBrandsReviewDto {
    private Long id;
    private String brand;
    private Long score;
    private String remarks;
    private String adsOwner;
    private LocalDateTime createDate;
    private LocalDateTime updateDate;

    public static ToolBrandsReviewDto fromEntity(ToolBrandsReview e) {
        if (e == null) return null;
        return new ToolBrandsReviewDto(
                e.getId(),
                e.getBrand(),
                e.getScore(),
                e.getRemarks(),
                e.getAdsOwner(),
                e.getCreateDate(),
                e.getUpdateDate()
        );
    }

    public ToolBrandsReview toEntity() {
        ToolBrandsReview e = new ToolBrandsReview();
        e.setId(this.id);
        e.setBrand(this.brand);
        e.setScore(this.score);
        e.setRemarks(this.remarks);
        e.setAdsOwner(this.adsOwner);
        e.setCreateDate(this.createDate);
        e.setUpdateDate(this.updateDate);
        return e;
    }
}
