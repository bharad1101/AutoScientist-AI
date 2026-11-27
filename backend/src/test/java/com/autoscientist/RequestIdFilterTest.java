package com.autoscientist;

import com.autoscientist.security.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesIdWhenHeaderMissingAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader(RequestIdFilter.HEADER));
        assertNull(MDC.get(RequestIdFilter.MDC_KEY), "MDC must be cleared after the request");
    }

    @Test
    void preservesSafeCallerSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestIdFilter.HEADER, "trace-abc.123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("trace-abc.123", response.getHeader(RequestIdFilter.HEADER));
    }

    @Test
    void replacesUnsafeCallerSuppliedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestIdFilter.HEADER, "bad value\r\ninjected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertNotNull(id);
        assertTrue(!id.contains("\n") && !id.contains("\r"), "header value must not allow log injection");
    }
}
