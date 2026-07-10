package com.admire.cars.runner.controller;

public class AuthLoginRequest {
    private String loginId;
    private String username;
    private String password;

    public String getLoginId() {
        if (loginId != null && !loginId.isBlank()) {
            return loginId;
        }
        return username;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
