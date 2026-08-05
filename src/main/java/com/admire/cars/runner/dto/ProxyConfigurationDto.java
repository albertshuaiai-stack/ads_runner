package com.admire.cars.runner.dto;


import lombok.*;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ProxyConfigurationDto {

    private String username;
    private String password;
    private String host;
    private int port;
}
