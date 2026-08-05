package com.admire.cars.runner.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ads.normal.task")
@Setter
@Getter
public class AdsConfig {


    private int maxRedirects = 10;

    private int connectTimeoutMillis = 5000;

    private int requestTimeoutMillis = 10000;

    private boolean ipVerification = true;

    private String ipLookupUrl = "https://ipapi.co/json/";

}
