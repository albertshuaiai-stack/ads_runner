package com.admire.cars.runner.config;

import com.admire.cars.runner.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Protect all API endpoints under /api/** except registration and health endpoints and login
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/users/register",
                        "/api/health/**",
                        "/api/auth/login",
                        // POST /api/postback* is released by AuthInterceptor; admin GET still requires a token
                        "/api/click",
                        "/api/normal/ads",
                        "/api/matrix/ads",
                        // Ads 脚本点击上报, 由 Controller 自行校验 api_key / script click report, api_key checked in controller
                        "/api/track/report",
                        "/api/track/daily");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.OPTIONS.name()
                )
                .allowedHeaders("*")
                .exposedHeaders("AMtoken")
                .maxAge(3600);
    }
}
