package com.admire.cars.runner.controller;

import com.admire.cars.runner.entity.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String userName,
        String userEmail,
        String userPhoneNumber,
        String apiKey,
        String userRole,
        LocalDateTime expireDate,
        Long normalAdsNumber,
        Long matrixAdsNumber,
        String status,
        LocalDateTime createDate,
        LocalDateTime updateDate) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUserName(),
                user.getUserEmail(),
                user.getUserPhoneNumber(),
                user.getApiKey(),
                user.getUserRole(),
                user.getExpireDate(),
                user.getNormalAdsNumber(),
                user.getMatrixAdsNumber(),
                user.getStatus(),
                user.getCreateDate(),
                user.getUpdateDate());
    }
}
