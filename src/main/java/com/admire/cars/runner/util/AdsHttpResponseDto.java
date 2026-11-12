package com.admire.cars.runner.util;


import lombok.Data;

@Data
public class AdsHttpResponseDto {

    private String status;

    private int code;

    private String url;

    private String error;

    // 本次请求写进联盟链接的点击ID / click id injected into the affiliate url for this request
    private String clickId;

    // 带点击ID的原始联盟请求URL / original affiliate request url carrying the click id
    private String requestUrl;

    public AdsHttpResponseDto() {
    }

    public AdsHttpResponseDto(String status, int code, String url, String error) {
        this.status = status;
        this.code = code;
        this.url = url;
        this.error = error;
    }
}
