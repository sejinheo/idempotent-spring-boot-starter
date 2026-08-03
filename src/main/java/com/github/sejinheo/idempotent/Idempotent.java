package com.github.sejinheo.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 어노테이션이 붙은 컨트롤러 메서드는 같은 Idempotency-Key 헤더로
 * 재요청이 오면 메서드를 다시 실행하지 않고 이전 응답을 그대로 반환한다.
 *
 * 지원 범위:
 * - HTTP 요청 전용 (Kafka 등 비-HTTP 컨텍스트 미지원)
 * - 순수 DTO 반환 및 ResponseEntity&lt;T&gt; 반환 모두 지원
 * - 키는 헤더 값 하나만 사용 (SpEL 키 미지원)
 * - TTL은 전역 설정(application.yml의 idempotency.ttl-hours)을 따름
 * - IN_PROGRESS TTL은 어노테이션에서 메서드별로 덮어쓸 수 있음
 * - 동시 요청은 REJECT만 지원 (409 반환)
 * - Redis 장애 시 요청을 실패 처리한다 (FAIL_CLOSED 고정, 503 반환)
 *
 * 사용 예:
 * <pre>
 * {@literal @}Idempotent
 * {@literal @}PostMapping("/products")
 * public ProductResponse createProduct(@RequestBody ProductRequest request) { ... }
 *
 * // 처리 시간이 긴 API는 inProgressTtlSeconds를 늘려서 중복 실행을 방지한다.
 * {@literal @}Idempotent(inProgressTtlSeconds = 60)
 * {@literal @}PostMapping("/payments")
 * public PaymentResponse createPayment(@RequestBody PaymentRequest request) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 멱등성 키로 사용할 HTTP 요청 헤더 이름.
     */
    String headerName() default "Idempotency-Key";

    /**
     * IN_PROGRESS 상태의 TTL(초 단위). 기본값 -1은 전역 설정(idempotency.in-progress-ttl-seconds)을 사용한다.
     * API의 최대 예상 처리 시간보다 길게 설정해야 처리 중 만료로 인한 중복 실행을 방지할 수 있다.
     */
    long inProgressTtlSeconds() default -1;
}
