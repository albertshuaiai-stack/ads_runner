package com.admire.cars.runner.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ads.normal.task")
@Setter
@Getter
public class AutoTaskConfig {


    @Value("${auto.task.max.redirects:10}")
    private int maxRedirects;

    @Value("${auto.task.connect.timeout.millis:5000}")
    private int connectTimeoutMillis;

    @Value("${auto.task.request.timeout.millis:10000}")
    private int requestTimeoutMillis;

    @Value("${ip.verification.switch:true}")
    private boolean ipVerification = true;

    @Value("${ip.lookup.url:https://ipapi.co/json/}")
    private String ipLookupUrl;

}
