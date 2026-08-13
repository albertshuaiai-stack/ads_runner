package com.admire.cars.runner.util;


import lombok.*;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AdsHttpResponseDto {


    private String status;

    private int code;

    private String url;

    private String error;
}
