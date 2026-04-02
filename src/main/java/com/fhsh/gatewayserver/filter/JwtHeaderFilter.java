package com.fhsh.gatewayserver.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 전역 필터: 인증된 사용자의 JWT 토큰에서 정보를 추출하여
 * 하위 서비스가 사용할 수 있도록 HTTP 헤더에 삽입합니다.
 */
@Component
public class JwtHeaderFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    /**
     * 필터 실행 순서 설정
     * 보안 필터가 토큰을 검증한 후에 실행되어야 하므로 우선순위를 낮게(숫자를 크게) 설정
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 5;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        return exchange.getPrincipal()
                .cast(Authentication.class)
                .flatMap(auth -> {

                    // JWT 아닌 경우 그냥 통과
                    if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
                        return chain.filter(exchange);
                    }

                    var jwt = jwtAuth.getToken();

                    String userId = jwt.getSubject(); // sub
                    String email = jwt.getClaimAsString("email");

                    // TODO: role은 나중에 Keycloak 붙일 때 수정
                    String role = "USER";

                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header("X-User-Id", userId)
                            .header("X-User-Email", email)
                            .header("X-User-Role", role)
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                .switchIfEmpty(chain.filter(exchange));
    }
}