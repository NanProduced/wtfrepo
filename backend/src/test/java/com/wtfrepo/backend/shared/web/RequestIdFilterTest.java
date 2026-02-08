package com.wtfrepo.backend.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void shouldReuseRequestIdFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdConstants.HEADER_NAME, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdConstants.HEADER_NAME)).isEqualTo("req-123");
        assertThat(request.getAttribute(RequestIdConstants.ATTRIBUTE_NAME)).isEqualTo("req-123");
    }

    @Test
    void shouldGenerateRequestIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String responseRequestId = response.getHeader(RequestIdConstants.HEADER_NAME);
        assertThat(responseRequestId).isNotBlank();
        assertThat(request.getAttribute(RequestIdConstants.ATTRIBUTE_NAME))
                .isEqualTo(responseRequestId);
    }

    @Test
    void shouldClearMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(MDC.get(RequestIdConstants.MDC_KEY)).isNull();
    }
}
