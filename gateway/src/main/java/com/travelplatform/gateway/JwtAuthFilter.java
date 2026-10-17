package com.travelplatform.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Validates the bearer token for protected routes and propagates the user id
 * downstream via the X-User-Id header. Public paths (register/login) pass
 * through untouched.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Set<String> PUBLIC_PATHS = Set.of("/auth/register", "/auth/login");
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    private final SecretKey key;

    public JwtAuthFilter(@Value("${auth.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (PUBLIC_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(authorization.substring(7))
                    .getPayload();
            String subject = claims.getSubject();
            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                role = "USER";
            }
            if (isAdminPath(path) && !"ADMIN".equals(role)) {
                exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
                return exchange.getResponse().setComplete();
            }
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .headers(headers -> {
                        headers.remove(USER_ID_HEADER);
                        headers.remove(USER_ROLE_HEADER);
                    })
                    .header(USER_ID_HEADER, subject)
                    .header(USER_ROLE_HEADER, role)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isAdminPath(String path) {
        return path.equals("/admin") || path.startsWith("/admin/");
    }
}
