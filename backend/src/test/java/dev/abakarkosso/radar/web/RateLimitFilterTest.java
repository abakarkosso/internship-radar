package dev.abakarkosso.radar.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final MutableClock clock = new MutableClock();
    private final RateLimitFilter filter = new RateLimitFilter(3, clock);

    @Test
    void allowsUpToTheLimitThenAnswers429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("/api/postings", "1.1.1.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call("/api/postings", "1.1.1.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void limitsEachClientSeparately() throws Exception {
        for (int i = 0; i < 4; i++) {
            call("/api/postings", "1.1.1.1");
        }
        assertThat(call("/api/postings", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void windowResetsAfterAMinute() throws Exception {
        for (int i = 0; i < 4; i++) {
            call("/api/postings", "1.1.1.1");
        }
        clock.advance(60_000);
        assertThat(call("/api/postings", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void memoryStaysBoundedUnderIpRotation() throws Exception {
        RateLimitFilter small = new RateLimitFilter(3, clock);
        for (int i = 0; i < 60_000; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/postings");
            request.setRemoteAddr("10.0." + (i / 256) + "." + (i % 256));
            small.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }
        assertThat(small.trackedClients()).isLessThanOrEqualTo(50_001);
    }

    @Test
    void doesNotLimitTheStaticSite() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("/assets/index.js", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse call(String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    static class MutableClock extends Clock {
        private long millis = Instant.parse("2026-10-02T12:00:00Z").toEpochMilli();

        void advance(long ms) {
            millis += ms;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
