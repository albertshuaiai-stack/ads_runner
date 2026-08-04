package com.admire.cars.runner.dto;

import lombok.*;

import com.fasterxml.jackson.databind.JsonNode;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BonusArriveCampaignItemDto {

    private String m_id;

    private String site_name;

    private String site_url;

    private String site_logo_url;

    private String update_time;

    private String region;

    private String merchant_status;

    private String tracking_url;

    private String deeplink;

    private JsonNode commissions;

    private String adv_catagory;


}
