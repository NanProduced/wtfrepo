package com.wtfrepo.backend.shared.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.web.ApiErrorResponse;
import com.wtfrepo.backend.shared.web.ErrorCode;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Blocks requests from banned users after authentication succeeds. */
@Component
public class UserBanEnforcementFilter extends OncePerRequestFilter {

  private static final String FALLBACK_ERROR_JSON =
      "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal server error\"}";

  private final UserBanPolicy userBanPolicy;
  private final JsonUtils jsonUtils;

  public UserBanEnforcementFilter(UserBanPolicy userBanPolicy, JsonUtils jsonUtils) {
    this.userBanPolicy = userBanPolicy;
    this.jsonUtils = jsonUtils;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication
        && jwtAuthentication.isAuthenticated()) {
      String userId = jwtAuthentication.getName();
      Optional<UserBanPolicy.UserBanSnapshot> banSnapshot = userBanPolicy.findActiveBan(userId);
      if (banSnapshot.isPresent()) {
        writeError(
            response,
            request,
            HttpStatus.FORBIDDEN,
            ErrorCode.FORBIDDEN,
            "User is banned");
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  private void writeError(
      HttpServletResponse response,
      HttpServletRequest request,
      HttpStatus httpStatus,
      ErrorCode errorCode,
      String message)
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
