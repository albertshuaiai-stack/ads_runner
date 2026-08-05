package com.admire.cars.runner.dto.bonusarrive;

import lombok.*;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BonusArriveCampaignResponseDto {


    private String status;

    private String info;

    private BonusArriveCampaignDto data;


}
