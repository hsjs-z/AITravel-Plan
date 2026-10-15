package com.travelplatform.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs one line per request carrying the active trace id, so a single operation
 * can be reconstructed across services from their plain logs (constitution VIII,
 * FR-042). Registered after Boot's tracing observation filter, so the MDC trace
 * id is already populated when this runs.
 */
public class TraceAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceAccessLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceContext.current() == null ? "-" : TraceContext.current();
        log.info("http {} {} traceId={}", request.getMethod(), request.getRequestURI(), traceId);
        filterChain.doFilter(request, response);
    }
}
