package com.admire.cars.runner.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 点击上下文信息, 仅用于记录, 不参与跳转决策
// Click context, recorded only; never used to decide the redirect target
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackClickMeta {

    private String ip;
    private String userAgent;
    private String referer;
    private String device;
    private String country;
}
