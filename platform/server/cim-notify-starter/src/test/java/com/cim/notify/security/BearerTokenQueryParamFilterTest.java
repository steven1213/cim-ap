package com.cim.notify.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenQueryParamFilterTest {

    private final BearerTokenQueryParamFilter filter = new BearerTokenQueryParamFilter();

    @Test
    void injectsBearerHeader_fromTokenParam() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("token", "abc");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> captured = new AtomicReference<>();
        FilterChain chain = (r, s) -> captured.set(((HttpServletRequest) r).getHeader("Authorization"));

        filter.doFilter(req, res, chain);

        assertThat(captured.get()).isEqualTo("Bearer abc");
    }

    @Test
    void passesThrough_whenExistingBearerHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer existing");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> captured = new AtomicReference<>();
        FilterChain chain = (r, s) -> captured.set(((HttpServletRequest) r).getHeader("Authorization"));

        filter.doFilter(req, res, chain);

        assertThat(captured.get()).isEqualTo("Bearer existing");
    }

    @Test
    void passesThrough_whenNoToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> captured = new AtomicReference<>();
        FilterChain chain = (r, s) -> captured.set(((HttpServletRequest) r).getHeader("Authorization"));

        filter.doFilter(req, res, chain);

        assertThat(captured.get()).isNull();
    }
}
