package com.wtfrepo.backend.admin.application;

import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogEntry;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuditLogQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAlertRecord;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminAuthResult;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminBootstrapRecord;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminManagerRecord;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminPrincipal;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminRoleRecord;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminSafetyTicketRecord;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanPage;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanQuery;
import com.wtfrepo.backend.admin.application.model.AdminModels.AdminUserBanRecord;
import com.wtfrepo.backend.admin.application.support.AdminConstants;
import com.wtfrepo.backend.admin.application.support.AdminExceptions;
import com.wtfrepo.backend.admin.application.support.AdminRequestFingerprintCalculator;
import com.wtfrepo.backend.admin.domain.AdminAuditActions;
import com.wtfrepo.backend.admin.domain.AdminAuditTargetType;
import com.wtfrepo.backend.admin.domain.AdminBanType;
import com.wtfrepo.backend.admin.domain.AdminRole;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketStatus;
import com.wtfrepo.backend.auth.application.AuthUserRecord;
import com.wtfrepo.backend.auth.application.AuthUserStore;
import com.wtfrepo.backend.auth.domain.AuthUser;
import com.wtfrepo.backend.auth.domain.UserRole;
import com.wtfrepo.backend.shared.idempotency.IdempotentOperationExecutor;
import com.wtfrepo.backend.shared.idempotency.IdempotencyConflictException;
import com.wtfrepo.backend.shared.security.IssuedToken;
import com.wtfrepo.backend.shared.security.TokenService;
import com.wtfrepo.backend.shared.security.TokenBlacklistStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminPlatformService {

  private static final Logger log = LoggerFactory.getLogger(AdminPlatformService.class);

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final AdminRoleStore adminRoleStore;
  private final AdminBootstrapStore adminBootstrapStore;
  private final AdminAuditLogStore adminAuditLogStore;
  private final AdminSafetyTicketStore adminSafetyTicketStore;
  private final AdminAlertStore adminAlertStore;
  private final AdminUserBanStore adminUserBanStore;
  private final AuthUserStore authUserStore;
  private final TokenService tokenService;
  private final TokenBlacklistStore tokenBlacklistStore;
  private final AdminRequestFingerprintCalculator requestFingerprintCalculator;
  private final IdempotentOperationExecutor idempotentExecutor;
  private final AdminPlatformProperties properties;

  public AdminPlatformService(
      AdminRoleStore adminRoleStore,
      AdminBootstrapStore adminBootstrapStore,
      AdminAuditLogStore adminAuditLogStore,
      AdminSafetyTicketStore adminSafetyTicketStore,
      AdminAlertStore adminAlertStore,
      AdminUserBanStore adminUserBanStore,
      AuthUserStore authUserStore,
      TokenService tokenService,
      TokenBlacklistStore tokenBlacklistStore,
      AdminRequestFingerprintCalculator requestFingerprintCalculator,
      IdempotentOperationExecutor idempotentExecutor,
      AdminPlatformProperties properties) {
    this.adminRoleStore = adminRoleStore;
    this.adminBootstrapStore = adminBootstrapStore;
    this.adminAuditLogStore = adminAuditLogStore;
    this.adminSafetyTicketStore = adminSafetyTicketStore;
    this.adminAlertStore = adminAlertStore;
    this.adminUserBanStore = adminUserBanStore;
    this.authUserStore = authUserStore;
    this.tokenService = tokenService;
    this.tokenBlacklistStore = tokenBlacklistStore;
    this.requestFingerprintCalculator = requestFingerprintCalculator;
    this.idempotentExecutor = idempotentExecutor;
    this.properties = properties;
  }

  public AdminAuthResult bootstrap(
      String requestId,
      String idempotencyKey,
      String userId,
      String email,
      String ipAddress,
      String userAgent) {
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedIdempotencyKey);
    String normalizedEmail = normalizeEmail(email);
    verifyBootstrapEmail(normalizedEmail);

    AuthUserRecord userRecord = loadUser(userId);
    String requestFingerprint =
        requestFingerprintCalculator.fingerprint(
            "bootstrap|" + userId + "|" + normalizedEmail);

    try {
      return idempotentExecutor
          .execute(
              "admin:bootstrap",
              resolvedIdempotencyKey,
              requestFingerprint,
              () -> {
                if (adminRoleStore.existsActiveRole(AdminRole.ADMIN)
                    || adminBootstrapStore.exists()) {
                  throw AdminExceptions.conflict(AdminConstants.Message.BOOTSTRAP_ALREADY_DONE);
                }

                AdminRoleRecord roleRecord =
                    adminRoleStore.grantRole(userId, AdminRole.ADMIN, null);
                AdminBootstrapRecord bootstrapRecord =
                    adminBootstrapStore.save(
                        new AdminBootstrapRecord(
                            userId, normalizedEmail, Instant.now(), ipAddress, userAgent));

                appendAuditLog(
                    userId,
                    AdminAuditActions.ADMIN_BOOTSTRAP,
                    AdminAuditTargetType.USER,
                    userId,
                    null,
                    null,
                    new BootstrapAuditMeta(bootstrapRecord.emailUsed()),
                    requestId,
                    ipAddress,
                    userAgent);

                IssuedToken token = issueAdminToken(userRecord, List.of(roleRecord));
                log.info(
                    "admin_bootstrap_success requestId={} userId={} email={}",
                    requestId,
                    userId,
                    normalizedEmail);
                return new AdminAuthResult(
                    token,
                    userRecord.userId(),
                    userRecord.username(),
                    roleRecordListToNames(List.of(roleRecord)));
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public AdminAuthResult login(
      String requestId,
      String idempotencyKey,
      String userId,
      String ipAddress,
      String userAgent) {
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedIdempotencyKey);

    AuthUserRecord userRecord = loadUser(userId);
    List<AdminRoleRecord> roles = adminRoleStore.findActiveRoles(userId);
    if (roles.isEmpty()) {
      throw AdminExceptions.forbidden(AdminConstants.Message.ADMIN_ROLE_REQUIRED);
    }

    String requestFingerprint =
        requestFingerprintCalculator.fingerprint(
            "login|" + userId + "|" + roleRecordListToNames(roles));

    try {
      return idempotentExecutor
          .execute(
              "admin:login:" + userId,
              resolvedIdempotencyKey,
              requestFingerprint,
              () -> {
                IssuedToken token = issueAdminToken(userRecord, roles);
                appendAuditLog(
                    userId,
                    AdminAuditActions.ADMIN_LOGIN,
                    AdminAuditTargetType.USER,
                    userId,
                    null,
                    null,
                    null,
                    requestId,
                    ipAddress,
                    userAgent);
                log.info("admin_login_success requestId={} userId={}", requestId, userId);
                return new AdminAuthResult(
                    token,
                    userRecord.userId(),
                    userRecord.username(),
                    roleRecordListToNames(roles));
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public AdminLogoutResult logout(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String tokenId,
      Instant tokenExpiresAt,
      String ipAddress,
      String userAgent) {
    requireAdmin(principal);
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedIdempotencyKey);

    if (!StringUtils.hasText(tokenId) || tokenExpiresAt == null) {
      return new AdminLogoutResult(false, null);
    }
    Duration ttl = Duration.between(Instant.now(), tokenExpiresAt);
    if (ttl.isZero() || ttl.isNegative()) {
      return new AdminLogoutResult(false, null);
    }

    tokenBlacklistStore.blacklist(tokenId, ttl);
    appendAuditLog(
        principal.userId(),
        AdminAuditActions.ADMIN_LOGOUT,
        AdminAuditTargetType.USER,
        principal.userId(),
        null,
        null,
        new LogoutAuditMeta(tokenId),
        requestId,
        ipAddress,
        userAgent);
    return new AdminLogoutResult(true, tokenExpiresAt);
  }

  public List<AdminManagerRecord> listManagers(AdminPrincipal principal) {
    requireSuperAdmin(principal);
    List<AdminRoleRecord> managerRoles = adminRoleStore.findActiveByRole(AdminRole.MANAGER);
    if (managerRoles.isEmpty()) {
      return List.of();
    }
    List<AdminManagerRecord> managers = new ArrayList<>(managerRoles.size());
    for (AdminRoleRecord record : managerRoles) {
      String username = resolveUsername(record.userId());
      managers.add(toManagerRecord(record, username));
    }
    return managers;
  }

  public AdminManagerRecord createManager(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String targetUserId,
      String ipAddress,
      String userAgent) {
    requireSuperAdmin(principal);
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedIdempotencyKey);

    AuthUserRecord target = loadUser(targetUserId);
    if (adminRoleStore.findActiveRole(targetUserId, AdminRole.ADMIN).isPresent()) {
      throw AdminExceptions.conflict(AdminConstants.Message.MANAGER_ALREADY_ADMIN);
    }

    String requestFingerprint =
        requestFingerprintCalculator.fingerprint(
            "manager_add|" + principal.userId() + "|" + targetUserId);

    try {
      return idempotentExecutor
          .execute(
              "admin:manager:add",
              resolvedIdempotencyKey,
              requestFingerprint,
              () -> {
                AdminRoleRecord existing =
                    adminRoleStore.findActiveRole(targetUserId, AdminRole.MANAGER).orElse(null);
                if (existing != null) {
                  return toManagerRecord(existing, target.username());
                }

                AdminRoleRecord roleRecord =
                    adminRoleStore.grantRole(targetUserId, AdminRole.MANAGER, principal.userId());
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.MANAGER_ADD,
                    AdminAuditTargetType.USER,
                    targetUserId,
                    null,
                    null,
                    new ManagerAuditMeta(targetUserId, roleRecord.role().name()),
                    requestId,
                    ipAddress,
                    userAgent);
                log.info(
                    "admin_manager_add requestId={} operatorId={} targetUserId={}",
                    requestId,
                    principal.userId(),
                    targetUserId);
                return toManagerRecord(roleRecord, target.username());
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public AdminManagerRecord revokeManager(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String targetUserId,
      String ipAddress,
      String userAgent) {
    requireSuperAdmin(principal);
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedIdempotencyKey);

    AuthUserRecord target = loadUser(targetUserId);
    String requestFingerprint =
        requestFingerprintCalculator.fingerprint(
            "manager_revoke|" + principal.userId() + "|" + targetUserId);

    try {
      return idempotentExecutor
          .execute(
              "admin:manager:revoke",
              resolvedIdempotencyKey,
              requestFingerprint,
              () -> {
                AdminRoleRecord roleRecord =
                    adminRoleStore
                        .revokeRole(targetUserId, AdminRole.MANAGER, principal.userId())
                        .orElseThrow(
                            () ->
                                AdminExceptions.notFound(
                                    AdminConstants.Message.MANAGER_NOT_FOUND));
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.MANAGER_REVOKE,
                    AdminAuditTargetType.USER,
                    targetUserId,
                    null,
                    null,
                    new ManagerAuditMeta(targetUserId, roleRecord.role().name()),
                    requestId,
                    ipAddress,
                    userAgent);
                log.info(
                    "admin_manager_revoke requestId={} operatorId={} targetUserId={}",
                    requestId,
                    principal.userId(),
                    targetUserId);
                return toManagerRecord(roleRecord, target.username());
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public AdminAuditLogPage listAuditLogs(
      AdminPrincipal principal,
      Integer page,
      Integer pageSize,
      String operatorId,
      String action,
      String targetType,
      String targetId) {
    int resolvedPage = normalizePage(page);
    int resolvedPageSize = normalizePageSize(pageSize);
    String resolvedOperatorId = operatorId;

    if (!hasRole(principal, AdminRole.ADMIN)) {
      resolvedOperatorId = principal.userId();
    }

    AdminAuditLogQuery query =
        new AdminAuditLogQuery(
            resolvedPage, resolvedPageSize, resolvedOperatorId, action, targetType, targetId);
    return adminAuditLogStore.list(query);
  }

  public AdminSafetyTicketPage listSafetyTickets(
      AdminPrincipal principal, Integer page, Integer pageSize, String status) {
    int resolvedPage = normalizePage(page);
    int resolvedPageSize = normalizePageSize(pageSize);
    AdminSafetyTicketStatus resolvedStatus = normalizeSafetyTicketStatus(status, true);
    AdminSafetyTicketQuery query =
        new AdminSafetyTicketQuery(resolvedPage, resolvedPageSize, resolvedStatus);
    return adminSafetyTicketStore.list(query);
  }

  public AdminSafetyTicketRecord updateSafetyTicket(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String ticketId,
      String status,
      String resolution,
      String ipAddress,
      String userAgent) {
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedIdempotencyKey);
    AdminSafetyTicketStatus targetStatus = normalizeSafetyTicketStatus(status, false);

    AdminSafetyTicketRecord current =
        adminSafetyTicketStore
            .findById(ticketId)
            .orElseThrow(
                () -> AdminExceptions.notFound(AdminConstants.Message.SAFETY_TICKET_NOT_FOUND));
    validateSafetyTicketTransition(current.status(), targetStatus);
    String normalizedResolution = normalizeSafetyTicketResolution(resolution, targetStatus);

    String requestFingerprint =
        requestFingerprintCalculator.fingerprint(
            "ticket_update|" + principal.userId() + "|" + ticketId + "|" + targetStatus.name());

    try {
      return idempotentExecutor
          .execute(
              "admin:safety-ticket:update",
              resolvedIdempotencyKey,
              requestFingerprint,
              () -> {
                Instant now = Instant.now();
                String resolvedBy = resolvedByForStatus(principal.userId(), targetStatus);
                Instant resolvedAt = resolvedAtForStatus(now, targetStatus);
                AdminSafetyTicketRecord updated =
                    adminSafetyTicketStore.updateStatus(
                        ticketId,
                        targetStatus,
                        normalizedResolution,
                        resolvedBy,
                        resolvedAt,
                        now);
                if (current.status() != targetStatus) {
                  appendAuditLog(
                      principal.userId(),
                      AdminAuditActions.TICKET_REVIEW,
                      AdminAuditTargetType.SAFETY_TICKET,
                      ticketId,
                      current,
                      updated,
                      new SafetyTicketAuditMeta(targetStatus.name(), normalizedResolution),
                      requestId,
                      ipAddress,
                      userAgent);
                }
                return updated;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public AdminAlertPage listAlerts(
      AdminPrincipal principal, Integer page, Integer pageSize, Boolean acknowledged) {
    requireSuperAdmin(principal);
    int resolvedPage = normalizePage(page);
    int resolvedPageSize = normalizePageSize(pageSize);
    Boolean resolvedAcknowledged = acknowledged != null ? acknowledged : Boolean.FALSE;
    AdminAlertQuery query = new AdminAlertQuery(resolvedPage, resolvedPageSize, resolvedAcknowledged);
    return adminAlertStore.list(query);
  }

  public AdminAlertRecord acknowledgeAlert(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String alertId,
      String ipAddress,
      String userAgent) {
    requireSuperAdmin(principal);
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedIdempotencyKey);

    AdminAlertRecord existing =
        adminAlertStore
            .findById(alertId)
            .orElseThrow(() -> AdminExceptions.notFound(AdminConstants.Message.ALERT_NOT_FOUND));

    String requestFingerprint =
        requestFingerprintCalculator.fingerprint(
            "alert_ack|" + principal.userId() + "|" + alertId);

    try {
      return idempotentExecutor
          .execute(
              "admin:alert:ack",
              resolvedIdempotencyKey,
              requestFingerprint,
              () -> {
                if (existing.acknowledged()) {
                  return existing;
                }
                AdminAlertRecord updated = adminAlertStore.acknowledge(alertId, principal.userId());
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.ALERT_ACK,
                    AdminAuditTargetType.ALERT,
                    alertId,
                    existing,
                    updated,
                    null,
                    requestId,
                    ipAddress,
                    userAgent);
                return updated;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public AdminUserBanRecord banUser(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String targetUserId,
      String banType,
      String reason,
      Instant expiresAt,
      String ipAddress,
      String userAgent) {
    requireSuperAdmin(principal);
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedIdempotencyKey);
    AdminBanType resolvedBanType = normalizeBanType(banType);
    validateBanRequest(resolvedBanType, expiresAt, reason);

    AuthUserRecord target = loadUser(targetUserId);
    String requestFingerprint =
        requestFingerprintCalculator.fingerprint(
            "user_ban|"
                + principal.userId()
                + "|"
                + targetUserId
                + "|"
                + resolvedBanType.name()
                + "|"
                + (expiresAt != null ? expiresAt.toString() : ""));

    try {
      return idempotentExecutor
          .execute(
              "admin:user:ban",
              resolvedIdempotencyKey,
              requestFingerprint,
              () -> {
                AdminUserBanRecord existing =
                    adminUserBanStore.findActiveByUserId(targetUserId).orElse(null);
                if (existing != null) {
                  return enrichUserBanRecord(existing);
                }

                Instant now = Instant.now();
                AdminUserBanRecord record =
                    new AdminUserBanRecord(
                        "ban_" + UUID.randomUUID(),
                        targetUserId,
                        target.username(),
                        resolvedBanType,
                        reason,
                        principal.userId(),
                        now,
                        expiresAt,
                        null,
                        null,
                        true);
                AdminUserBanRecord saved = adminUserBanStore.save(record);
                AdminUserBanRecord enriched = enrichUserBanRecord(saved);
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.USER_BAN,
                    AdminAuditTargetType.USER,
                    targetUserId,
                    null,
                    enriched,
                    new BanAuditMeta(resolvedBanType.name(), reason, expiresAt),
                    requestId,
                    ipAddress,
                    userAgent);
                return enriched;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public AdminUserBanRecord unbanUser(
      AdminPrincipal principal,
      String requestId,
      String idempotencyKey,
      String targetUserId,
      String ipAddress,
      String userAgent) {
    requireSuperAdmin(principal);
    String resolvedIdempotencyKey = resolveIdempotencyKey(requestId, idempotencyKey);
    validateIdempotencyKey(resolvedIdempotencyKey);

    AuthUserRecord target = loadUser(targetUserId);
    String requestFingerprint =
        requestFingerprintCalculator.fingerprint(
            "user_unban|" + principal.userId() + "|" + targetUserId);

    try {
      return idempotentExecutor
          .execute(
              "admin:user:unban",
              resolvedIdempotencyKey,
              requestFingerprint,
              () -> {
                AdminUserBanRecord existing =
                    adminUserBanStore
                        .findActiveByUserId(targetUserId)
                        .orElseThrow(
                            () -> AdminExceptions.notFound(AdminConstants.Message.BAN_NOT_FOUND));
                AdminUserBanRecord updated =
                    new AdminUserBanRecord(
                        existing.id(),
                        existing.userId(),
                        target.username(),
                        existing.banType(),
                        existing.reason(),
                        existing.bannedBy(),
                        existing.bannedAt(),
                        existing.expiresAt(),
                        principal.userId(),
                        Instant.now(),
                        false);
                AdminUserBanRecord saved = adminUserBanStore.save(updated);
                AdminUserBanRecord enriched = enrichUserBanRecord(saved);
                appendAuditLog(
                    principal.userId(),
                    AdminAuditActions.USER_UNBAN,
                    AdminAuditTargetType.USER,
                    targetUserId,
                    existing,
                    enriched,
                    null,
                    requestId,
                    ipAddress,
                    userAgent);
                return enriched;
              })
          .response();
    } catch (IdempotencyConflictException ex) {
      throw AdminExceptions.conflict(AdminConstants.Message.IDEMPOTENCY_CONFLICT);
    }
  }

  public AdminUserBanPage listBannedUsers(
      AdminPrincipal principal, Integer page, Integer pageSize, Boolean active) {
    requireSuperAdmin(principal);
    int resolvedPage = normalizePage(page);
    int resolvedPageSize = normalizePageSize(pageSize);
    AdminUserBanQuery query = new AdminUserBanQuery(resolvedPage, resolvedPageSize, active);
    AdminUserBanPage pageResult = adminUserBanStore.list(query);
    List<AdminUserBanRecord> enriched =
        pageResult.items().stream().map(this::enrichUserBanRecord).toList();
    return new AdminUserBanPage(
        enriched,
        pageResult.page(),
        pageResult.pageSize(),
        pageResult.total(),
        pageResult.totalPages());
  }

  private void verifyBootstrapEmail(String email) {
    String expected = properties.getBootstrap().getEmail();
    if (!StringUtils.hasText(expected)) {
      return;
    }
    if (!expected.trim().equalsIgnoreCase(email)) {
      throw AdminExceptions.forbidden(AdminConstants.Message.BOOTSTRAP_EMAIL_MISMATCH);
    }
  }

  private AuthUserRecord loadUser(String userId) {
    return authUserStore
        .findByUserId(userId)
        .orElseThrow(() -> AdminExceptions.notFound(AdminConstants.Message.USER_NOT_FOUND));
  }

  private IssuedToken issueAdminToken(AuthUserRecord record, List<AdminRoleRecord> adminRoles) {
    AuthUser authUser = toAuthUser(record, adminRoles);
    return tokenService.issueToken(authUser);
  }

  private AuthUser toAuthUser(AuthUserRecord record, List<AdminRoleRecord> adminRoles) {
    Set<UserRole> roles = new LinkedHashSet<>();
    roles.add(UserRole.USER);
    for (AdminRoleRecord adminRole : adminRoles) {
      roles.add(UserRole.valueOf(adminRole.role().name()));
    }
    return new AuthUser(
        record.userId(),
        record.username(),
        record.usernameChanged(),
        roles,
        record.bugBalance());
  }

  private void requireSuperAdmin(AdminPrincipal principal) {
    if (!hasRole(principal, AdminRole.ADMIN)) {
      throw AdminExceptions.forbidden(AdminConstants.Message.FORBIDDEN_ADMIN);
    }
  }

  private void requireAdmin(AdminPrincipal principal) {
    if (!hasRole(principal, AdminRole.ADMIN) && !hasRole(principal, AdminRole.MANAGER)) {
      throw AdminExceptions.forbidden(AdminConstants.Message.FORBIDDEN_ADMIN);
    }
  }

  private boolean hasRole(AdminPrincipal principal, AdminRole role) {
    if (principal == null || principal.roles() == null) {
      return false;
    }
    return principal.roles().stream().anyMatch(r -> r.equalsIgnoreCase(role.name()));
  }

  private AdminManagerRecord toManagerRecord(AdminRoleRecord record, String username) {
    return new AdminManagerRecord(
        record.userId(),
        username,
        record.role().name(),
        record.active(),
        record.grantedBy(),
        record.grantedAt(),
        record.revokedBy(),
        record.revokedAt());
  }

  private String resolveIdempotencyKey(String requestId, String idempotencyKey) {
    if (StringUtils.hasText(idempotencyKey)) {
      return idempotencyKey.trim();
    }
    return requestId;
  }

  private void validateIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 128) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_IDEMPOTENCY_KEY);
    }
  }

  private String normalizeEmail(String email) {
    if (!StringUtils.hasText(email)) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_EMAIL);
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private List<String> roleRecordListToNames(List<AdminRoleRecord> records) {
    return records.stream()
        .map(record -> record.role().name())
        .sorted()
        .collect(Collectors.toList());
  }

  private String resolveUsername(String userId) {
    String username =
        authUserStore.findByUserId(userId).map(AuthUserRecord::username).orElse(null);
    return StringUtils.hasText(username) ? username : userId;
  }

  private void appendAuditLog(
      String operatorId,
      String action,
      String targetType,
      String targetId,
      Object beforeSnapshot,
      Object afterSnapshot,
      Object metadata,
      String requestId,
      String ipAddress,
      String userAgent) {
    AdminAuditLogEntry entry =
        new AdminAuditLogEntry(
            "aal_" + UUID.randomUUID(),
            operatorId,
            action,
            targetType,
            targetId,
            beforeSnapshot,
            afterSnapshot,
            metadata,
            requestId,
            ipAddress,
            userAgent,
            Instant.now());
    adminAuditLogStore.append(entry);
  }

  private int normalizePage(Integer page) {
    if (page == null || page < 1) {
      return 1;
    }
    return page;
  }

  private int normalizePageSize(Integer pageSize) {
    if (pageSize == null || pageSize < 1) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(pageSize, MAX_PAGE_SIZE);
  }

  private AdminSafetyTicketStatus normalizeSafetyTicketStatus(String status, boolean allowDefault) {
    if (!StringUtils.hasText(status)) {
      if (allowDefault) {
        return AdminSafetyTicketStatus.OPEN;
      }
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_SAFETY_TICKET_STATUS);
    }
    String value = status.trim();
    if ("ALL".equalsIgnoreCase(value)) {
      if (allowDefault) {
        return null;
      }
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_SAFETY_TICKET_STATUS);
    }
    try {
      return AdminSafetyTicketStatus.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_SAFETY_TICKET_STATUS);
    }
  }

  private void validateSafetyTicketTransition(
      AdminSafetyTicketStatus current, AdminSafetyTicketStatus target) {
    if (current == target) {
      return;
    }
    boolean valid =
        switch (current) {
          case OPEN -> target == AdminSafetyTicketStatus.IN_REVIEW;
          case IN_REVIEW ->
              target == AdminSafetyTicketStatus.ACTIONED
                  || target == AdminSafetyTicketStatus.CLOSED;
          case ACTIONED -> target == AdminSafetyTicketStatus.CLOSED;
          case CLOSED -> false;
        };
    if (!valid) {
      throw AdminExceptions.conflict(AdminConstants.Message.INVALID_SAFETY_TICKET_TRANSITION);
    }
  }

  private String normalizeSafetyTicketResolution(
      String resolution, AdminSafetyTicketStatus status) {
    if (status == AdminSafetyTicketStatus.ACTIONED
        || status == AdminSafetyTicketStatus.CLOSED) {
      if (!StringUtils.hasText(resolution)) {
        throw AdminExceptions.validation(AdminConstants.Message.INVALID_SAFETY_TICKET_STATUS);
      }
      return resolution.trim();
    }
    if (!StringUtils.hasText(resolution)) {
      return null;
    }
    return resolution.trim();
  }

  private String resolvedByForStatus(String operatorId, AdminSafetyTicketStatus status) {
    if (status == AdminSafetyTicketStatus.ACTIONED || status == AdminSafetyTicketStatus.CLOSED) {
      return operatorId;
    }
    return null;
  }

  private Instant resolvedAtForStatus(Instant now, AdminSafetyTicketStatus status) {
    if (status == AdminSafetyTicketStatus.ACTIONED || status == AdminSafetyTicketStatus.CLOSED) {
      return now;
    }
    return null;
  }

  private void validateBanRequest(AdminBanType banType, Instant expiresAt, String reason) {
    if (banType == null) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BAN_TYPE);
    }
    if (!StringUtils.hasText(reason)) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BAN_REQUEST);
    }
    if (banType == AdminBanType.TEMPORARY && expiresAt == null) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BAN_REQUEST);
    }
    if (banType == AdminBanType.PERMANENT && expiresAt != null) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BAN_REQUEST);
    }
  }

  private AdminBanType normalizeBanType(String banType) {
    if (!StringUtils.hasText(banType)) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BAN_TYPE);
    }
    try {
      return AdminBanType.valueOf(banType.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw AdminExceptions.validation(AdminConstants.Message.INVALID_BAN_TYPE);
    }
  }

  private AdminUserBanRecord enrichUserBanRecord(AdminUserBanRecord record) {
    if (record == null) {
      return null;
    }
    String username = resolveUsername(record.userId());
    return new AdminUserBanRecord(
        record.id(),
        record.userId(),
        username,
        record.banType(),
        record.reason(),
        record.bannedBy(),
        record.bannedAt(),
        record.expiresAt(),
        record.unbannedBy(),
        record.unbannedAt(),
        record.active());
  }

  private record BootstrapAuditMeta(String email) {}

  private record ManagerAuditMeta(String userId, String role) {}

  private record SafetyTicketAuditMeta(String status, String resolution) {}

  private record BanAuditMeta(String banType, String reason, Instant expiresAt) {}

  private record LogoutAuditMeta(String tokenId) {}

  public record AdminLogoutResult(boolean revoked, Instant expiresAt) {}
}
