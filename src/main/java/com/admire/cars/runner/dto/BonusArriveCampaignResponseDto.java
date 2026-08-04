package com.admire.cars.runner.dto;

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
