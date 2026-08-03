package com.github.sejinheo.idempotent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml의 idempotency.* 프로퍼티와 바인딩된다.
 *
 * 예:
 * idempotency:
 *   key-prefix: "order-service:idempotency:"
 *   ttl-hours: 24
 *   in-progress-ttl-seconds: 30
 *   on-storage-failure: FAIL_CLOSED
 *   storage: redis   # redis(기본) 또는 jdbc
 */
@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyProperties {

    /**
     * Redis에 저장되는 키의 prefix.
     * 여러 MS가 같은 Redis를 공유하는 경우 서비스별로 다르게 설정해서
     * 키 충돌을 막는다. 서비스명을 넣는 걸 권장.
     */
    private final String keyPrefix;

    /**
     * 캐시된 응답의 TTL(시간 단위). v1에서는 전역 하나만 적용하고
     * 어노테이션별 개별 TTL은 지원하지 않는다.
     */
    private final long ttlHours;

    /**
     * IN_PROGRESS 상태의 TTL(초 단위).
     * JVM 크래시 등으로 release()가 호출되지 않았을 때 키가 자동 만료되는 시간.
     * API의 최대 예상 처리 시간보다 길게 설정해야 처리 중 만료로 인한 중복 실행을 방지할 수 있다.
     */
    private final long inProgressTtlSeconds;

    /**
     * Redis 등 저장소 장애 시 동작 정책.
     * FAIL_CLOSED(기본): 예외를 그대로 던져서 요청을 실패 처리한다.
     * FAIL_OPEN: 예외를 무시하고 요청을 통과시킨다. 중복 실행 가능성을 감수한다.
     */
    private final FailurePolicy onStorageFailure;

    /**
     * 멱등성 저장소 구현체 선택.
     * REDIS(기본): Redis SET NX 기반. 분산 환경에 적합.
     * JDBC: DB 기반. 비즈니스 트랜잭션과 같은 트랜잭션으로 묶어 강한 멱등성 보장.
     *       결제처럼 절대 중복 실행이 안 되는 경우에 권장.
     */
    private final StorageType storage;

    public IdempotencyProperties(
            @DefaultValue("idempotency:") String keyPrefix,
            @DefaultValue("24") long ttlHours,
            @DefaultValue("30") long inProgressTtlSeconds,
            @DefaultValue("FAIL_CLOSED") FailurePolicy onStorageFailure,
            @DefaultValue("REDIS") StorageType storage) {
        this.keyPrefix = keyPrefix;
        this.ttlHours = ttlHours;
        this.inProgressTtlSeconds = inProgressTtlSeconds;
        this.onStorageFailure = onStorageFailure;
        this.storage = storage;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public long getTtlHours() {
        return ttlHours;
    }

    public long getInProgressTtlSeconds() {
        return inProgressTtlSeconds;
    }

    public FailurePolicy getOnStorageFailure() {
        return onStorageFailure;
    }

    public StorageType getStorage() {
        return storage;
    }
}
