# idempotent-spring-boot-starter

Redis 기반 HTTP 멱등성 처리 Spring Boot Starter 라이브러리입니다.

## 설치

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.sejinheo:idempotent-spring-boot-starter:v0.2.1'
}
```

## 설정 (application.yml)

```yaml
idempotency:
  key-prefix: "order-service:idempotency:"  # 서비스별로 다르게 설정 (Redis 키 충돌 방지)
  ttl-hours: 24                              # COMPLETED 응답 보관 TTL (기본: 24시간)
  in-progress-ttl-seconds: 30               # 처리 중 상태 TTL (기본: 30초, API 처리 시간에 맞게 조정)

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

## 사용법

`@Idempotent`를 컨트롤러 메서드에 붙이면 됩니다. 순수 DTO 반환과 `ResponseEntity<T>` 반환 모두 지원합니다.

```java
@Idempotent
@PostMapping("/orders")
public ResponseEntity<Void> createOrder(@RequestBody CreateOrderRequest request) {
    orderService.create(request);
    return ResponseEntity.ok().build();
}
```

클라이언트는 요청마다 `Idempotency-Key` 헤더에 UUID 등 고유값을 담아 보내야 하고,
네트워크 재시도나 버튼 중복 클릭 시에도 **같은 시도에 대해서는 같은 키**를 재사용해야 합니다.

```
POST /orders
Idempotency-Key: 3f29b6b2-1c2a-4e2e-9a2e-1234567890ab
```

## 응답 규칙

| 상황 | 응답 |
|---|---|
| 헤더 없음 | 400 Bad Request |
| 같은 키로 첫 요청 | 정상 실행 |
| 같은 키 + 같은 바디 재요청 (처리 완료 후) | 캐시된 응답 그대로 재생 |
| 같은 키 + 같은 바디 재요청 (처리 중) | 409 Conflict |
| 같은 키 + 다른 바디 | 422 Unprocessable Entity |
| Redis 장애 | 503 Service Unavailable |

## 지원 범위

- HTTP 요청 전용 (Kafka 등 비-HTTP 컨텍스트 미지원)
- 순수 DTO 반환 및 `ResponseEntity<T>` 반환 모두 지원
- 키는 헤더 값 하나만 사용 (SpEL 키 미지원)
- TTL은 전역 설정으로 고정 (COMPLETED: `ttl-hours`, IN_PROGRESS: `in-progress-ttl-seconds`)
- 동시 요청은 REJECT만 지원 (409 반환)
- Redis 장애 시 요청 실패 처리 (FAIL_CLOSED 고정, 503 반환)

## 로컬 테스트

Docker가 실행 중이어야 합니다 (Testcontainers가 Redis 컨테이너를 띄웁니다).

```bash
./gradlew test
```
