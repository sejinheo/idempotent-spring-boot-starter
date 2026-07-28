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
 * - 동시 요청은 REJECT만 지원 (409 반환)
 * - Redis 장애 시 요청을 실패 처리한다 (FAIL_CLOSED 고정, 503 반환)
 *
 * 사용 예:
 * <pre>
 * {@literal @}Idempotent
 * {@literal @}PostMapping("/products")
 * public ProductResponse createProduct(@RequestBody ProductRequest request) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 멱등성 키로 사용할 HTTP 요청 헤더 이름.
     */
    String headerName() default "Idempotency-Key";
}
