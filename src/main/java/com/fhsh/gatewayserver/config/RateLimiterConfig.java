package com.fhsh.gatewayserver.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Principal;

/**
 * API 호출 횟수 제한(Rate Limiting)을 위한 키 식별 설정 클래스입니다.
 * Redis 등과 연동하여 특정 시간 동안 허용할 요청 횟수를 관리할 때 사용됩니다.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * 사용자를 식별하는 '키(Key)'를 결정하는 Bean입니다.
     * 1순위: 인증된 사용자의 ID (Username)
     * 2순위: 인증되지 않은 경우 사용자의 IP 주소
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange ->
                exchange.getPrincipal()
                        .map(Principal::getName) // userId
                        // 만약 로그인하지 않은 사용자라면
                        .defaultIfEmpty(
                                // 접속한 사용자의 IP 주소를 식별 키로 사용
                                exchange.getRequest()
                                        .getRemoteAddress()
                                        .getAddress()
                                        .getHostAddress()
                        );
    }
}
