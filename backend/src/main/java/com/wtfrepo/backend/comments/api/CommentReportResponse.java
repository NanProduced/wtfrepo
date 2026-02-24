package com.wtfrepo.backend.comments.api;

import com.wtfrepo.backend.comments.application.CommentService;

public record CommentReportResponse(boolean reported, String ticketId) {

  public static CommentReportResponse from(CommentService.ReportResult result) {
    return new CommentReportResponse(result.reported(), result.ticketId());
  }
}

