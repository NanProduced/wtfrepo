package com.wtfrepo.backend.comments.domain;

import java.util.Arrays;
import org.springframework.util.StringUtils;

/** Supported report reasons in M05 comments contract. */
public enum CommentReportReasonCode {
  POLITICAL,
  PORN,
  HATE,
  SPAM,
  COPYRIGHT,
  OTHER;

  public static CommentReportReasonCode fromRaw(String rawReasonCode) {
    if (!StringUtils.hasText(rawReasonCode)) {
      return null;
    }
    String normalized = rawReasonCode.trim().toUpperCase();
    return Arrays.stream(values())
        .filter(reasonCode -> reasonCode.name().equals(normalized))
        .findFirst()
        .orElse(null);
  }
}

