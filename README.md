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

| 헤더 키      | 설명                         | 예시 값                                   |
| ------------ | ---------------------------- | ----------------------------------------- |
| X-User-Id    | 사용자의 UUID (Keycloak sub) | UUID 값                                   |
| X-User-Email | 사용자의 이메일 주소         | test@test.com                             |
| X-User-Role  | 사용자 권한                  | ADMIN or HUB_ADMIN or DELIVERY or COMPANY |

---

## 🧩 서비스에서 사용하는 방법

각 서비스에서는 `@RequestHeader`를 통해 사용자 정보를 간단하게 사용할 수 있습니다.

### 📌 Controller 예시

```java
@GetMapping("/api/v1/users/test")
public String test(
   @RequestHeader(value = "X-User-Id", required = false) String userId,
   @RequestHeader(value = "X-User-Email", required = false) String email,
   @RequestHeader(value = "X-User-Role", required = false) String role
) {
   if("ADMIN".equals(role)) {
      return "userId=" + userId + ", email=" + email + ", role=" + role;
   }
   return "userId=" + userId + ", email=" + email;
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

## 🛡️ 안정성 및 보안 설정

### 1. Rate Limiting (요청 제한)

- Redis를 사용하여 유저별/IP별 트래픽을 제어합니다.

- 로그인 유저는 userId 기반, 비로그인 유저는 Remote IP 기반으로 키를 생성하여 무분별한 API 호출을 방지합니다.

### 2. Circuit Breaker & Fallback

- 특정 서비스 응답이 지연되거나 장애가 발생하면 즉시 Fallback 응답을 반환합니다.

- 응답 규격 (503 Service Unavailable):

```JSON
{
  "status": 503,
  "service": "user-service",
  "message": "다잇다 서비스가 일시적으로 지연되고 있습니다. 잠시 후 다시 시도해주세요.",
  "timestamp": "2026-04-03T..."
}
```

> ⚠️ Circuit Breaker (Resilience4j)를 사용하기 위해서는 각자 서비스에 등록해야합니다.

## Circuit Breaker 등록 방법

- **사용법**: 외부 호출이 발생하는 Service 메서드에 `@CircuitBreaker`를 붙이세요.
- **Fallback**: 호출 실패 시 사용자에게 돌려줄 **기본 응답 로직**을 반드시 작성하세요.
- **주의**: Fallback 메서드는 원본 메서드와 반환 타입이 같아야 하며, 마지막 파라미터로 `Throwable`을 받아야 합니다.

### 1. 의존성 추가 (build.gradle)

```gradle
dependencies {
   implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
   implementation 'org.springframework.boot:spring-boot-starter-aop'

   // 아직 infra-repo에는 prometheus가 없습니다. 추가하시고 주석처리 해주세요.
   // docker에 올라가면 공지 하겠습니다!
   runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
}
```

저는 위 예시처럼 github에서 의존성을 가져왔더니 안돼서 아래 의존성을 추가했습니다.

```gradle
implementation 'org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j'
runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
```

### 2. 로직에 적용 (Annotation 방식)

각 서비스의 로직의 필요한 곳에 추가하시면 됩니다.
아래는 예시이며 각자 비즈니스 로직에 맞게 사용하시면 됩니다.

```java
@Service
@Slf4j
public class OrderService {

    // 1. Circuit Breaker 적용 (이름은 설정파일과 매칭)
    // 2. fallbackMethod는 같은 클래스 내에 정의되어야 함
    @CircuitBreaker(name = "orderService", fallbackMethod = "fallbackGetProductDetails")
    public ProductResponse getProductDetails(Long productId) {

        // 외부 서비스(Product Service) 호출 로직 (RestTemplate or FeignClient)
        // 여기서 에러가 나거나 응답이 늦으면 Circuit이 열립니다.
        return productClient.getProduct(productId);
    }

    // Fallback 메서드 (원본 메서드와 파라미터가 같아야 하며, Exception 객체가 추가됨)
    public ProductResponse fallbackGetProductDetails(Long productId, Throwable t) {
        log.error("Product service is down! Cause: {}", t.getMessage());

        // 에러 발생 시 돌려줄 가짜(Mock) 데이터 혹은 기본값
        return new ProductResponse(productId, "임시 상품 정보", 0L, "현재 정보를 불러올 수 없습니다.");
    }
}
```

### 3. 설정 파일 작성 (application.yml)

`project-configs/configs/common` 에서 일괄적으로 관리하겠습니다.

혹시 각 서비스마다 실패율이나 설정을 조정하고 싶다면 저에게 말씀해주세요.

---

## 📝 참고

- 내부 서비스 간 호출은 `/internal/**` 경로 사용
- Gateway를 거치지 않는 내부 호출은 인증 헤더 사용하지 않음
- 모든 외부 요청은 반드시 Gateway를 통해 진입
