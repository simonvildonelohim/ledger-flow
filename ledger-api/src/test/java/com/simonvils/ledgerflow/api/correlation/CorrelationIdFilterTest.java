package com.simonvils.ledgerflow.api.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests for {@link CorrelationIdFilter}. No Spring context, no database. */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesAnIdWhenTheClientSuppliesNone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seen = new String[1];

        filter.doFilter(request, response, capturing(seen));

        assertThat(seen[0]).isNotBlank();
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(seen[0]);
    }

    @Test
    void honoursAnIdTheClientSupplies() throws Exception {
        String supplied = "caller-" + UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, supplied);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seen = new String[1];

        // A caller that already has an id for its own request can hand it over,
        // and one search then spans both sides. Generating unconditionally would
        // break that chain at every hop.
        filter.doFilter(request, response, capturing(seen));

        assertThat(seen[0]).isEqualTo(supplied);
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo(supplied);
    }

    @Test
    void generatesAnIdWhenTheHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seen = new String[1];

        filter.doFilter(request, response, capturing(seen));

        assertThat(seen[0]).isNotBlank();
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        filter.doFilter(
                new MockHttpServletRequest(), new MockHttpServletResponse(), capturing(new String[1]));

        // Servlet threads are pooled. An id left behind would attach itself to an
        // unrelated request and send an investigation down the wrong path.
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void clearsTheMdcEvenWhenTheRequestFails() {
        FilterChain failing =
                (request, response) -> {
                    throw new IllegalStateException("something downstream went wrong");
                };

        try {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing);
        } catch (Exception expected) {
            // The failure itself is not what is under test.
        }

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    /** A chain that records what the MDC held while the request was in flight. */
    private static FilterChain capturing(String[] seen) {
        return (request, response) -> seen[0] = MDC.get(CorrelationId.MDC_KEY);
    }
}
