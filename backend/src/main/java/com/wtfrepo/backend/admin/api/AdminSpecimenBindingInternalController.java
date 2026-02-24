package com.wtfrepo.backend.admin.api;

import com.wtfrepo.backend.admin.application.AdminSpecimenIdentityBindingService;
import com.wtfrepo.backend.admin.application.AdminSpecimenIdentityBindingService.SpecimenIdentityBindingRecord;
import com.wtfrepo.backend.shared.web.RequestIdConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/internal/specimen-bindings")
@Tag(name = "Internal: Specimen Binding", description = "Internal specimen identity binding lookups.")
public class AdminSpecimenBindingInternalController {

  private final AdminSpecimenIdentityBindingService bindingService;

  public AdminSpecimenBindingInternalController(AdminSpecimenIdentityBindingService bindingService) {
    this.bindingService = bindingService;
  }

  @GetMapping
  @Operation(summary = "Resolve specimen identity binding")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Binding resolved")})
  public ResponseEntity<SpecimenBindingResponse> resolve(
      @Parameter(
              in = ParameterIn.HEADER,
              name = RequestIdConstants.HEADER_NAME,
              description = "Request correlation id.",
              required = true)
          @RequestHeader(RequestIdConstants.HEADER_NAME)
          String requestId,
      @Parameter(description = "Specimen id.", required = true)
          @RequestParam("specimenId")
          String specimenId,
      @Parameter(description = "User id.", required = true)
          @RequestParam("userId")
          String userId,
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
    AdminApiSupport.requireAdminPrincipal(jwt);
    SpecimenIdentityBindingRecord record = bindingService.resolveBinding(specimenId, userId);
    return ResponseEntity.ok(SpecimenBindingResponse.from(record));
  }

  public record SpecimenBindingResponse(
      String specimenId,
      String userId,
      String role,
      String source,
      boolean active,
      String githubUserId,
      String githubLogin) {

    static SpecimenBindingResponse from(SpecimenIdentityBindingRecord record) {
      return new SpecimenBindingResponse(
          record.specimenId(),
          record.userId(),
          record.roleSnapshot() != null ? record.roleSnapshot().name() : null,
          record.source(),
          record.active(),
          record.githubUserId(),
          record.githubLogin());
    }
  }
}
