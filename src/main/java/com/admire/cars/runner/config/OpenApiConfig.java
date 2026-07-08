package com.admire.cars.runner.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ADS Runner API",
                version = "v1",
                description = "API documentation for ADS Runner services"
        ),
        security = @SecurityRequirement(name = "AMtoken")
)
@SecurityScheme(
        name = "AMtoken",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "AMtoken",
        description = "JWT token from /api/auth/login response (amToken)"
)
public class OpenApiConfig {
}
