package com.admire.cars.runner.controller;

import java.time.LocalDateTime;

public record AuthLoginResponse(
        String amToken,
        LocalDateTime expireDate,
        String userName,
        String userRole,
        String roles,
        Long normalAdsTotalCount,
        Long matrixAdsTotalCount) {
}
