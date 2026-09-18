package com.hieu.edurepo.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Adds a server-generated request ID without trusting a client-controlled forwarding header. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-ID";
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Async/SSE work must not retain servlet-thread MDC after the initial dispatch returns.
            MDC.remove(MDC_KEY);
        }
    }
}
