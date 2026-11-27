package com.autoscientist.security;

import com.autoscientist.dto.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Token-bucket rate limiting (Bucket4j) applied to API traffic.
 *
 * <p>Limits are per authenticated principal when available, otherwise per
 * resolved client IP. Health/readiness/actuator endpoints and CORS preflight
 * requests are excluded so orchestration probes can never be throttled.</p>
 *
 * <p>Buckets live in a bounded access-ordered (LRU) map. When the configured
 * limit is reached, the least recently used bucket is evicted, so an attacker
 * who rotates identities cannot grow the map without bound and cannot evict an
 * actively used bucket. This is a single-node limiter by design; a shared-store
 * or gateway limiter is required for horizontally scaled deployments.</p>
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN_CLIENT = "unknown";

    private final ObjectMapper objectMapper;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
            return size() > Math.max(1, maxBuckets);
        }
    };

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.capacity:120}")
    private long capacity;

    @Value("${app.rate-limit.refill-tokens:120}")
    private long refillTokens;

    @Value("${app.rate-limit.refill-minutes:1}")
    private long refillMinutes;

    @Value("${app.rate-limit.max-buckets:10000}")
    private int maxBuckets;

    /** Number of proxy hops whose X-Forwarded-For entries are trusted. */
    @Value("${app.rate-limit.trusted-proxy-count:0}")
    private int trustedProxyCount;

    public RateLimitingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return path.equals("/api/health")
                || path.startsWith("/api/health/")
                || path.startsWith("/actuator")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = clientKey(request);
        Bucket bucket;
        synchronized (buckets) {
            bucket = buckets.get(key);
            if (bucket == null) {
                bucket = newBucket();
                buckets.put(key, bucket);
            }
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, probe.getRemainingTokens())));

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.error("Too many requests. Please retry after " + retryAfterSeconds + " seconds.");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(refillTokens, Duration.ofMinutes(refillMinutes))))
                .build();
    }

    private String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            return "user:" + authentication.getName();
        }
        return "ip:" + clientIp(request);
    }

    /**
     * X-Forwarded-For is only consulted when the deployment explicitly declares
     * how many reverse proxies it trusts. With the default of zero hops, a
     * client-supplied forwarded header is ignored completely.
     */
    private String clientIp(HttpServletRequest request) {
        if (trustedProxyCount > 0) {
            String forwarded = request.getHeader(X_FORWARDED_FOR);
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",", -1);
                int index = parts.length - trustedProxyCount - 1;
                if (index >= 0) {
                    String candidate = parts[index].trim();
                    if (isIpLiteral(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        String remote = request.getRemoteAddr();
        return isIpLiteral(remote) ? remote : UNKNOWN_CLIENT;
    }

    private static boolean isIpLiteral(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.indexOf(':') >= 0) {
            // Only attempt literal parsing for IPv6-shaped text; never trigger DNS.
            if (!value.matches("[0-9a-fA-F:.%\\[\\]]+")) {
                return false;
            }
            try {
                InetAddress.getByName(value);
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3
                    || !octet.chars().allMatch(Character::isDigit)) {
                return false;
            }
            int parsed = Integer.parseInt(octet);
            if (parsed < 0 || parsed > 255) {
                return false;
            }
        }
        return true;
    }
}
