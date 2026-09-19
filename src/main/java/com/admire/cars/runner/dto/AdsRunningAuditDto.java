package com.admire.cars.runner.dto;

import java.time.LocalDateTime;

public class AdsRunningAuditDto {
    private Long id;
    private String brand;
    private String platform;
    private String email;
    private String adsOwner;
    private LocalDateTime createDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAdsOwner() { return adsOwner; }
    public void setAdsOwner(String adsOwner) { this.adsOwner = adsOwner; }
    public LocalDateTime getCreateDate() { return createDate; }
    public void setCreateDate(LocalDateTime createDate) { this.createDate = createDate; }
}
