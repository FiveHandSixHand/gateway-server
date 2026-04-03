package com.fhsh.gatewayserver.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.route.Route;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    /**
     * 모든 서비스에서 공통으로 사용할 수 있는 통합 폴백 메서드입니다.
     * 어떤 서비스에서 장애가 발생했는지 로그를 남기고 사용자에게 안내합니다.
     */
    @GetMapping("/common")
    public ResponseEntity<Map<String, Object>> commonFallback(ServerWebExchange exchange) {
        // 장애가 발생한 서비스의 Route 정보 추출
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String serviceName = (route != null) ? route.getId() : "Unknown Service";

        log.error("Fallback 호출됨: 서비스 [{}]에 연결할 수 없거나 응답 시간이 초과되었습니다.", serviceName);

        // 2. 응답 데이터 구성
        Map<String, Object> response = new HashMap<>();
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", String.format("[%s] 이용이 일시적으로 제한되었습니다. 잠시 후 다시 시도해주세요.", serviceName));
        response.put("service", serviceName);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}