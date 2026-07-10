package com.admire.cars.runner.event;

public record AdsAutoTaskRegistrationEvent(
        AdsAutoTaskAction action,
        Long adsId,
        String adsOwner,
        String adsType,
        Long intervalTime,
        String status) {
}
