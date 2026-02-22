package com.wtfrepo.backend.economy.api;

import com.wtfrepo.backend.economy.application.EconomyWalletService;
import com.wtfrepo.backend.economy.application.support.EconomyConstants;
import com.wtfrepo.backend.economy.application.support.EconomyExceptions;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Wallet APIs defined in Arena contract shared economy scope. */
@RestController
@Validated
@RequestMapping("/api/v1/wallet")
public class WalletController {

  private final EconomyWalletService economyWalletService;

  public WalletController(EconomyWalletService economyWalletService) {
    this.economyWalletService = economyWalletService;
  }

  @GetMapping
  public ResponseEntity<WalletResponse> wallet(@AuthenticationPrincipal Jwt jwt) {
    String userId = requireUserId(jwt);
    EconomyWalletService.WalletView walletView = economyWalletService.getWallet(userId);
    return ResponseEntity.ok(WalletResponse.from(walletView));
  }

  @GetMapping("/ledger")
  public ResponseEntity<WalletLedgerResponse> ledger(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(50) Integer limit,
      @RequestParam(required = false) String reason) {
    String userId = requireUserId(jwt);
    EconomyWalletService.LedgerPageView ledgerPage =
        economyWalletService.getLedger(userId, cursor, limit, reason);
    return ResponseEntity.ok(WalletLedgerResponse.from(ledgerPage));
  }

  @PostMapping("/daily")
  public ResponseEntity<WalletDailyClaimResponse> claimDaily(@AuthenticationPrincipal Jwt jwt) {
    String userId = requireUserId(jwt);
    EconomyWalletService.DailyClaimResult result = economyWalletService.claimDaily(userId);
    return ResponseEntity.ok(WalletDailyClaimResponse.from(result));
  }

  private String requireUserId(Jwt jwt) {
    if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
      throw EconomyExceptions.unauthorized(EconomyConstants.Message.AUTH_REQUIRED);
    }
    return jwt.getSubject();
  }
}
