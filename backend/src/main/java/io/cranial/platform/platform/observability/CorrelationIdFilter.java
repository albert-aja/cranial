package io.cranial.platform.platform.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[a-zA-Z0-9._\\-]{1,80}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(CorrelationIdHolder.HEADER_NAME);
        String correlationId = (incoming != null && SAFE_CORRELATION_ID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();

        MDC.put(CorrelationIdHolder.MDC_KEY, correlationId);
        response.setHeader(CorrelationIdHolder.HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIdHolder.MDC_KEY);
            MDC.remove(CorrelationIdHolder.MDC_APP_KEY);
        }
    }
}

