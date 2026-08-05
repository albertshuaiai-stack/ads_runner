package com.admire.cars.runner.dto;


import lombok.*;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class IpVerificationDto {

    private String ip;
    private String countryCode;
    private boolean matched;

}
