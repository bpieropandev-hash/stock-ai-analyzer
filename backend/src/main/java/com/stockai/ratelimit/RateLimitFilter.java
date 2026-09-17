package com.stockai.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Limita requisições por IP nos endpoints que disparam análise via LLM
 * (custo real por chamada) — ver docs/ai/anti-patterns.md § Segurança.
 * Redis fora do ar: falha aberta (permite a requisição, loga warn) — proteção
 * de custo não deve derrubar a funcionalidade principal por causa de si mesma.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private record LimitedRoute(String method, String pattern) {}

    private static final List<LimitedRoute> LIMITED_ROUTES = List.of(
            new LimitedRoute("GET", "/api/stocks/*/analysis"),
            new LimitedRoute("POST", "/api/stocks/*/analysis/refresh"),
            new LimitedRoute("GET", "/api/compare"),
            new LimitedRoute("POST", "/api/simulate")
    );

    private final StringRedisTemplate redisTemplate;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.rate-limit.requests-per-minute:20}")
    private int requestsPerMinute;

    /**
     * IPs de proxy reverso confiáveis — só nesse caso o X-Forwarded-For é usado
     * para identificar o cliente real. Sem isso configurado, qualquer cliente
     * poderia forjar o header e trocar de "IP" a cada requisição, zerando o
     * contador e contornando o limite por completo (achado do security-reviewer).
     * Vazio por padrão: usa sempre request.getRemoteAddr().
     */
    @Value("${app.rate-limit.trusted-proxy-ips:}")
    private Set<String> trustedProxyIps;

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !isLimitedRoute(request)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = clientIp(request);
        String key = "ratelimit:%s:%d".formatted(clientIp, System.currentTimeMillis() / WINDOW.toMillis());

        long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count == 1) {
                redisTemplate.expire(key, WINDOW.plusSeconds(10));
            }
        } catch (Exception e) {
            log.warn("Rate limiter indisponível (Redis fora do ar?), permitindo requisição: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        if (count > requestsPerMinute) {
            log.warn("Rate limit excedido: ip={} path={} count={}", clientIp, request.getRequestURI(), count);
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Limite de requisições excedido, tente novamente em instantes\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isLimitedRoute(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return LIMITED_ROUTES.stream()
                .anyMatch(route -> route.method().equals(method) && PATH_MATCHER.match(route.pattern(), path));
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustedProxyIps.contains(remoteAddr)) {
            return remoteAddr;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }
}
