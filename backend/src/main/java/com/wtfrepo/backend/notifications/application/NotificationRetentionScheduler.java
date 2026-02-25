package com.wtfrepo.backend.notifications.application;

import com.wtfrepo.backend.notifications.infra.persistence.repository.UserNotificationJpaRepository;
import java.time.Duration;
import java.time.Instant;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Scheduled cleanup for notification retention window. */
@Component
@ConditionalOnBean({EntityManagerFactory.class, UserNotificationJpaRepository.class})
public class NotificationRetentionScheduler {

  private static final Logger log = LoggerFactory.getLogger(NotificationRetentionScheduler.class);

  private final UserNotificationJpaRepository notificationRepository;
  private final NotificationsPolicyProperties policyProperties;

  public NotificationRetentionScheduler(
      UserNotificationJpaRepository notificationRepository,
      NotificationsPolicyProperties policyProperties) {
    this.notificationRepository = notificationRepository;
    this.policyProperties = policyProperties;
  }

  @Scheduled(cron = "${app.notifications.cleanup-cron:0 0 3 * * *}", zone = "Asia/Shanghai")
  @Transactional
  public void cleanupExpiredNotifications() {
    int retentionDays = policyProperties.getRetentionDays();
    if (retentionDays <= 0) {
      return;
    }
    Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
    int deleted = notificationRepository.deleteByCreatedAtBefore(cutoff);
    log.info(
        "notify_retention_cleanup_done retentionDays={} deleted={} cutoff={}",
        retentionDays,
        deleted,
        cutoff);
  }
}
