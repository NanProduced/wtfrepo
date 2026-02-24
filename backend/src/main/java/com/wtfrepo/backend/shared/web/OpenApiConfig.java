package com.wtfrepo.backend.shared.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
    info = @Info(title = "WTF-Repo Backend API", version = "v1"),
    security = {@SecurityRequirement(name = "BearerAuth")})
@SecurityScheme(
    name = "BearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

  @Bean
  public GroupedOpenApi adminApi() {
    return GroupedOpenApi.builder().group("admin").pathsToMatch("/api/v1/admin/**").build();
  }

  @Bean
  public GroupedOpenApi internalApi() {
    return GroupedOpenApi.builder().group("internal").pathsToMatch("/api/v1/internal/**").build();
  }

  @Bean
  public GroupedOpenApi publicApi() {
    return GroupedOpenApi.builder()
        .group("public")
        .pathsToMatch("/api/v1/**")
        .pathsToExclude("/api/v1/admin/**", "/api/v1/internal/**")
        .build();
  }
}
