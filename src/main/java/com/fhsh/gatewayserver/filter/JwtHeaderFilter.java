package com.fhsh.gatewayserver.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Map;

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

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    // JWT 아닌 경우 그냥 통과
                    if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
                        return chain.filter(exchange);
                    }

                    var jwt = jwtAuth.getToken();

                    // 기본 정보 추출
                    String userId = jwt.getSubject();
                    String email = jwt.getClaimAsString("email");

                    // 전역 권한(realm_access) 추출
                    String role = extractSingleRole(jwt);

                    // 헤더 추가하여 요청 변조
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header(HEADER_USER_ID, safe(userId))
                            .header(HEADER_USER_EMAIL, safe(email))
                            .header(HEADER_USER_ROLES, role) // 단일 권한 그대로 삽입
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                })
                // 데이터가 없을 때
                .switchIfEmpty(chain.filter(exchange)); // 인증 안 된 유저도 접근 가능한 API를 위해 필요
    }

    /**
     * JWT 내의 realm_access 클레임에서 권한(Roles) 리스트를 추출하는 헬퍼 메서드
     * Keycloak의 표준 토큰 구조인 { "realm_access": { "roles": ["ADMIN", "USER"] } }를 파싱합니다.
     */
    private String extractSingleRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
            // 리스트의 첫 번째 요소를 가져오되, 없으면 빈 문자열
            return roles.stream()
                    .map(Object::toString)
                    .findFirst()
                    .orElse("");
        }
        return "";
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}