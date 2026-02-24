package com.wtfrepo.backend.shared.security;

import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.web.ApiErrorResponse;
import com.wtfrepo.backend.shared.web.ErrorCode;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import com.fasterxml.jackson.core.JsonProcessingException;

@Configuration
@EnableConfigurationProperties(SecurityTokenProperties.class)
public class SecurityConfig {

  private static final String FALLBACK_ERROR_JSON =
      "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal server error\"}";

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JsonUtils jsonUtils)
      throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/exchange")
                    .permitAll()
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/v1/me/**")
                    .authenticated()
                    .requestMatchers("/api/v1/wallet/**")
                    .authenticated()
                    .requestMatchers("/api/v1/bet/**")
                    .authenticated()
                    .requestMatchers("/api/v1/settlement/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/game/score")
                    .authenticated()
                    .requestMatchers("/api/v1/watchlist/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/comments/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/comments/**")
                    .authenticated()
                    .requestMatchers("/api/v1/admin/**")
                    .authenticated()
                    .requestMatchers("/api/v1/internal/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/specimens/*/hype")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/arena/vote")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) ->
                            writeError(
                                response,
                                request,
                                HttpStatus.UNAUTHORIZED,
                                ErrorCode.UNAUTHORIZED,
                                "Authentication required",
                                jsonUtils))
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) ->
                            writeError(
                                response,
                                request,
                                HttpStatus.FORBIDDEN,
                                ErrorCode.FORBIDDEN,
                                "Access denied",
                                jsonUtils)))
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

    return http.build();
  }

  @Bean
  JwtEncoder jwtEncoder(SecurityTokenProperties tokenProperties) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(tokenProperties.secretKey()));
  }

  @Bean
  JwtDecoder jwtDecoder(
      SecurityTokenProperties tokenProperties,
      ObjectProvider<TokenBlacklistStore> blacklistStoreProvider) {
    JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(tokenProperties.secretKey()).build();
    TokenBlacklistStore blacklistStore = blacklistStoreProvider.getIfAvailable();
    if (blacklistStore == null) {
      return decoder;
    }
    return new BlacklistAwareJwtDecoder(decoder, blacklistStore);
  }

  private void writeError(
      HttpServletResponse response,
      HttpServletRequest request,
      HttpStatus httpStatus,
      ErrorCode errorCode,
      String message,
      JsonUtils jsonUtils)
      throws IOException {
    ApiErrorResponse body =
        ApiErrorResponse.of(
            Instant.now(),
            httpStatus.value(),
            httpStatus.getReasonPhrase(),
            errorCode,
            message,
            request.getRequestURI(),
            requestId(request),
            null);

    response.setStatus(httpStatus.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    try {
      response.getWriter().write(jsonUtils.toJson(body));
    } catch (JsonProcessingException ex) {
      response.getWriter().write(FALLBACK_ERROR_JSON);
    }
  }

  private String requestId(HttpServletRequest request) {
    Object attr = request.getAttribute(RequestIdConstants.ATTRIBUTE_NAME);
    return attr != null ? String.valueOf(attr) : null;
  }
}
