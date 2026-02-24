package com.wtfrepo.backend.admin.infra.outbox;

import com.wtfrepo.backend.admin.application.AdminSafetyTicketStore;
import com.wtfrepo.backend.admin.domain.AdminSafetyTicketSource;
import com.wtfrepo.backend.shared.outbox.OutboxStreamEventHandler;
import com.wtfrepo.backend.shared.outbox.OutboxStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Handles M05 {@code CommentReportedEvent} for admin safety ticket intake. */
@Component
public class CommentReportedOutboxEventHandler implements OutboxStreamEventHandler {

  private static final Logger log =
      LoggerFactory.getLogger(CommentReportedOutboxEventHandler.class);

  private static final String EVENT_TYPE = "CommentReportedEvent";

  private final AdminOutboxPayloadReader payloadReader;
  private final AdminSafetyTicketStore safetyTicketStore;

  public CommentReportedOutboxEventHandler(
      AdminOutboxPayloadReader payloadReader, AdminSafetyTicketStore safetyTicketStore) {
    this.payloadReader = payloadReader;
    this.safetyTicketStore = safetyTicketStore;
  }

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  @Override
  public void handle(OutboxStreamMessage message) {
    String payload = message.payload();
    String reportId = payloadReader.readTextField(payload, "reportId").orElse(null);
    String commentId = payloadReader.readTextField(payload, "commentId").orElse(null);
    String specimenId = payloadReader.readTextField(payload, "specimenId").orElse(null);
    String reporterUserId = payloadReader.readTextField(payload, "reporterUserId").orElse(null);
    String reasonCode = payloadReader.readEnumLikeUppercaseField(payload, "reasonCode").orElse(null);
    String reportMessage = payloadReader.readTextField(payload, "message").orElse(null);

    if (!StringUtils.hasText(commentId) || !StringUtils.hasText(reporterUserId)) {
      log.warn(
          "admin_comment_report_payload_invalid eventType={} eventId={} reason=missing_comment_or_reporter",
          EVENT_TYPE,
          message.eventId());
      return;
    }

    String ticketId = StringUtils.hasText(reportId) ? "tkt_" + reportId : null;
    String reason = buildReason(reportId, reasonCode, reportMessage, specimenId);

    safetyTicketStore.create(
        AdminSafetyTicketSource.COMMENT_REPORT,
        reporterUserId,
        "COMMENT",
        commentId,
        reason,
        ticketId);
  }

  private String buildReason(
      String reportId, String reasonCode, String reportMessage, String specimenId) {
    StringBuilder builder = new StringBuilder();
    if (StringUtils.hasText(reasonCode)) {
      builder.append(reasonCode.trim());
    }
    if (StringUtils.hasText(reportMessage)) {
      if (builder.length() > 0) {
        builder.append(" | ");
      }
      builder.append(reportMessage.trim());
    }
    if (builder.length() == 0) {
      builder.append("comment_report");
    }
    if (StringUtils.hasText(specimenId)) {
      builder.append(" (specimenId=").append(specimenId.trim()).append(")");
    }
    if (StringUtils.hasText(reportId)) {
      builder.append(" [reportId=").append(reportId.trim()).append("]");
    }
    return builder.toString();
  }
}
