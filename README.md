# 🚪 API Gateway Server

## 📌 개요

API Gateway Server는 MSA 환경에서 모든 클라이언트 요청의 **단일 진입점(Single Entry Point)** 역할을 수행합니다.
각 서비스로의 라우팅, 인증(JWT 검증), 공통 필터링을 담당하며 시스템의 **보안과 흐름을 제어** 합니다.

---

## 🎯 주요 기능

- JWT 기반 인증 처리 (Keycloak 연동)
- 서비스별 요청 라우팅 (Spring Cloud Gateway)
- 유레카 기반 서비스 디스커버리 연동
- 사용자 정보 헤더 전달
- 인증되지 않은 요청 차단

---

## 🏗️ 라우팅 규격

| Service ID               | Target URI                    | Path                                        |
| ------------------------ | ----------------------------- | ------------------------------------------- |
| user-service             | lb://user-service             | /api/v1/users/\*\*                          |
| auth-service             | lb://user-service             | /api/v1/auth/\*\*                           |
| hub-service              | lb://hub-service              | /api/v1/hubs/**, /api/v1/hub-inventories/** |
| delivery-service         | lb://delivery-service         | /api/v1/deliveries/\*\*                     |
| delivery-manager-service | lb://delivery-manager-service | /api/v1/delivery-managers/\*\*              |
| company-service          | lb://company-service          | /api/v1/companies/\*\*                      |
| order-service            | lb://order-service            | /api/v1/orders/\*\*                         |
| notification-service     | lb://notification-service     | /api/v1/slackmessages/\*\*                  |
| product-service          | lb://product-service          | /api/v1/products/\*\*                       |

> ⚠️ `internal/**` 경로는 내부 서비스 간 통신 전용으로 Gateway 라우팅에서 제외합니다.

---

## 🔐 인증 및 내부 헤더 전달 가이드

Gateway는 JWT 검증 후 사용자 정보를 추출하여 내부 서비스로 전달합니다.

### 📥 클라이언트 요청

```http
Authorization: Bearer {JWT_TOKEN}
```

---

### 📤 Gateway → 서비스 전달 헤더

```http
X-User-Id: {userId}
X-User-Email: {email}
X-User-Role: {role}
```

---

## 🧩 서비스에서 사용하는 방법

각 서비스에서는 `@RequestHeader`를 통해 사용자 정보를 간단하게 사용할 수 있습니다.

### 📌 Controller 예시

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String email,
            @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "email", email,
                "role", role
        ));
    }
}
```

---

## 🔄 요청 처리 파이프라인

```plaintext
1. Client 요청
   ↓
2. API Gateway 진입
   ↓
3. JWT 토큰 검증 (Keycloak)
   ↓
4. 사용자 정보 추출
   ↓
5. 내부 헤더 생성 (X-User-Id 등)
   ↓
6. Eureka 통해 대상 서비스 조회
   ↓
7. 해당 서비스로 요청 전달
   ↓
8. 서비스에서 비즈니스 로직 처리
   ↓
9. 응답 반환
```

---

## ⚙️ 인프라 기동 순서

```plaintext
1. Infra-repo
2. Eureka Server
3. Config Server
4. 각 서비스 (user, order, etc.)
5. API Gateway (마지막)
```

---

## 📈 기대 효과

- 인증 로직을 Gateway에 집중하여 **보안 강화**
- 서비스 간 결합도 감소
- 단일 진입점 제공으로 **확장성 향상**
- 간단한 헤더 기반 사용자 정보 전달로 개발 생산성 향상

---

## 📝 참고

- 내부 서비스 간 호출은 `/internal/**` 경로 사용
- Gateway를 거치지 않는 내부 호출은 인증 헤더 사용하지 않음
- 모든 외부 요청은 반드시 Gateway를 통해 진입
