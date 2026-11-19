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

    // 单次换链执行的硬截止时间, 防止底层 HTTP 卡死占住调度线程 / hard deadline of one link run so a stuck HTTP call cannot hold a scheduler thread
    @Value("${auto.task.hard.deadline.millis:120000}")
    private long hardDeadlineMillis;

    @Value("${ip.verification.switch:true}")
    private boolean ipVerification = true;

    @Value("${ip.lookup.url:https://api.country.is/}")
    private String ipLookupUrl;

}
