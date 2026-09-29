package com.admire.cars.runner.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackRedirectResult {

    // 点击唯一标识, 透传给联盟用于回传归因 / click id passed to the network for attribution
    private String clickId;

    private String siteId;

    private String targetUrl;
}
