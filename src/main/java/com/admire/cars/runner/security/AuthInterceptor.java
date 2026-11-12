package com.admire.cars.runner.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final java.util.Set<String> PUBLIC_POSTBACK_PATHS = java.util.Set.of(
            "/api/postback", "/api/pb/postback", "/api/yp/postback");

    private final JwtTokenService tokenService;

    public AuthInterceptor(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        // 平台转化回调只认 POST, 不带登录态; 同路径的 GET 是后台查询, 仍要求登录
        // platform postbacks are POST only; the admin GET on the same path still requires a token
        if (HttpMethod.POST.matches(request.getMethod())
                && PUBLIC_POSTBACK_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        // Accept AMtoken header primarily, fallback to Authorization: Bearer <token>
        String token = null;
        String amHeader = request.getHeader("AMtoken");
        if (amHeader != null && !amHeader.isBlank()) {
            token = amHeader;
        } else {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        if (token != null) {
            Long userId = tokenService.validateToken(token);
            if (userId != null) {
                request.setAttribute("userId", userId);
                return true;
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("Unauthorized");
        return false;
    }
}
