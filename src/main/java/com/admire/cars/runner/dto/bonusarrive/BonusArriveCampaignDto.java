package com.admire.cars.runner.dto.bonusarrive;

import lombok.*;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BonusArriveCampaignDto {


    private Long total_items;

    private Long total_page;

    @JsonAlias("list")
    private List<BonusArriveCampaignItemDto> items;
}
