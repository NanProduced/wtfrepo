package com.wtfrepo.backend.economy.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints placeholder for contract paths deferred to M07.
 *
 * <p>Current implementation keeps interface discoverable while preventing accidental business
 * coupling before admin contract is finalized.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class EconomyAdminPlaceholderController {

  @PostMapping("/economy/grant-bug")
  public ResponseEntity<AdminPlaceholderResponse> grantBugPlaceholder() {
    return notImplemented("/api/v1/admin/economy/grant-bug");
  }

  @PostMapping("/betting/force-settle")
  public ResponseEntity<AdminPlaceholderResponse> forceSettlePlaceholder() {
    return notImplemented("/api/v1/admin/betting/force-settle");
  }

  @PostMapping("/betting/house-config")
  public ResponseEntity<AdminPlaceholderResponse> houseConfigPlaceholder() {
    return notImplemented("/api/v1/admin/betting/house-config");
  }

  private ResponseEntity<AdminPlaceholderResponse> notImplemented(String path) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
        .body(
            new AdminPlaceholderResponse(
                "ADMIN_CONTRACT_PENDING",
                path + " is reserved for M07 admin contract and is not implemented yet."));
  }

  private record AdminPlaceholderResponse(String code, String message) {}
}

