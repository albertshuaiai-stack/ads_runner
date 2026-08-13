package com.admire.cars.runner.util;

import lombok.*;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AdsHttpRequestDto {


    private String affiliateUrl;

    private String landingPageUrl;

    private String deviceType;

    private String userAgent;


}
