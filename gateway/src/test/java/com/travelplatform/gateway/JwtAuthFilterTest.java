package com.travelplatform.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtAuthFilterTest {

    private static final String SECRET = "gateway-test-secret-0123456789-abcdef";

    private final JwtAuthFilter filter = new JwtAuthFilter(SECRET);

    @Test
    void adminTokenIsForwardedWithRoleAndSpoofedHeadersAreRemoved() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/probe")
                        .header("Authorization", "Bearer " + token("admin-1", "ADMIN"))
                        .header("X-User-Id", "spoofed-user")
                        .header("X-User-Role", "ADMIN")
                        .build());

        filter.filter(exchange, chain(forwarded)).block();

        assertNotNull(forwarded.get());
        assertEquals("admin-1", forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("ADMIN", forwarded.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void userTokenIsRejectedAtGatewayForAdminPath() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin/probe")
                        .header("Authorization", "Bearer " + token("user-1", "USER"))
                        .build());

        filter.filter(exchange, chain(forwarded)).block();

        assertNull(forwarded.get());
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }

    @Test
    void legacyTokenWithoutRoleIsTreatedAsUser() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/resources/search")
                        .header("Authorization", "Bearer " + token("legacy-1", null))
                        .build());

        filter.filter(exchange, chain(forwarded)).block();

        assertNotNull(forwarded.get());
        assertEquals("USER", forwarded.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void missingTokenIs401() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/resources/search").build());

        filter.filter(exchange, chain(forwarded)).block();

        assertNull(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    private GatewayFilterChain chain(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private String token(String subject, String role) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(new java.util.Date());
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
