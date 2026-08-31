package com.simonvils.ledgerflow.api.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id and puts it where the logger will find it.
 *
 * <p>Ordered first so the id is in place before anything else can log. A filter
 * that runs after security or after request logging leaves a gap at exactly the
 * point where an incident usually starts.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /**
     * Takes the client's id when one is supplied, generates one otherwise.
     *
     * <p>Honouring an inbound id is what makes this useful across a system larger
     * than this one: a caller that already has an id for its own request can hand
     * it over, and one search then spans both sides. Generating unconditionally
     * would break that chain at every hop.
     *
     * <p>The id is echoed back on the response so a client — or whoever is holding
     * a failed request in a terminal — can quote it when reporting the problem.
     *
     * <p>The MDC is cleared in a finally block. Servlet threads are pooled and
     * reused, so an id left behind would attach itself to an unrelated request and
     * quietly send an investigation down the wrong path.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationId.HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
