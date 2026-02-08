package com.wtfrepo.backend.shared.web;

public final class RequestIdConstants {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE_NAME = RequestIdConstants.class.getName() + ".requestId";

    private RequestIdConstants() {}
}
