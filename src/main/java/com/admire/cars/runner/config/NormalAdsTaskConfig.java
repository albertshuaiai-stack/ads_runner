package com.admire.cars.runner.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ads.normal.task")
public class NormalAdsTaskConfig {


    private int maxRedirects = 10;

    private int connectTimeoutMillis = 5000;

    private int requestTimeoutMillis = 10000;

    private boolean ipVerification = true;

    private String ipLookupUrl = "https://ipapi.co/json/";


    public int getMaxRedirects() {
        return maxRedirects;
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    public boolean isIpVerification() {
        return ipVerification;
    }

    public void setIpVerification(boolean ipVerification) {
        this.ipVerification = ipVerification;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public void setRequestTimeoutMillis(int requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public String getIpLookupUrl() {
        return ipLookupUrl;
    }

    public void setIpLookupUrl(String ipLookupUrl) {
        this.ipLookupUrl = ipLookupUrl;
    }


}
