package dev.abakarkosso.radar.web;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client fixed-window limit on the API, so one scraper can't starve the database.
 *
 * In memory on purpose: one instance is all this app needs today. Running several
 * instances would need a shared store (Redis) instead; see DECISIONS.md.
 */
@Component
class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 50_000;

    private record Window(long start, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limitPerMinute;
    private final Clock clock;

    RateLimitFilter(@Value("${radar.api.requests-per-minute:120}") int limitPerMinute, Clock clock) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
    }

    int trackedClients() {
        return windows.size();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW_MILLIS);
            if (windows.size() > MAX_TRACKED_CLIENTS) {
                // ponytail: under a wide IP-rotation flood, forget everyone rather than grow without bound.
                // Upgrade path is an edge rate limit (Cloudflare / the platform's), not a bigger map.
                windows.clear();
            }
        }
        // getRemoteAddr is the real client only when forward headers are trusted (server.forward-headers-strategy).
        Window w = windows.compute(request.getRemoteAddr(), (ip, old) ->
                old == null || now - old.start() >= WINDOW_MILLIS ? new Window(now, 1) : new Window(old.start(), old.count() + 1));

        if (w.count() > limitPerMinute) {
            long retryAfter = Math.max(1, (w.start() + WINDOW_MILLIS - now) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"title\":\"Too many requests\",\"status\":429,"
                    + "\"detail\":\"Slow down and try again in " + retryAfter + " seconds.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
