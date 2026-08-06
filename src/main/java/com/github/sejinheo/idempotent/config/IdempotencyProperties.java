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
 *   on-claim-failure: FAIL_CLOSED
 *   storage: redis   # redis(기본) 또는 jdbc
 *   schema-version: 1  # DTO 구조가 바뀌는 배포 시 올린다
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
     * 저장소 선점(tryClaim) 실패 시 동작 정책.
     * FAIL_CLOSED(기본): 예외를 그대로 던져서 요청을 실패 처리한다.
     * FAIL_OPEN: 예외를 무시하고 요청을 통과시킨다. 중복 실행 가능성을 감수한다.
     *            통과 시 WARN 로그를 남긴다.
     *
     * 결과 저장(complete) 실패는 이 설정과 무관하게 항상 결과를 반환하고 WARN 로그를 남긴다.
     *
     * 주의: FAIL_OPEN이 실제로 동작하려면 Redis 커넥션 타임아웃이 충분히 짧아야 한다.
     * Redis가 죽지 않고 느리기만 해도 타임아웃 전까지는 예외가 발생하지 않으므로
     * FAIL_OPEN 정책이 의미 없어진다. Spring Data Redis의 커넥션 타임아웃을
     * 서비스 요구사항에 맞게 짧게 설정할 것을 권장한다.
     * (예: spring.data.redis.timeout=500ms)
     */
    private final ClaimFailurePolicy onClaimFailure;

    /**
     * 멱등성 저장소 구현체 선택.
     * REDIS(기본): Redis SET NX 기반. 분산 환경에 적합.
     * JDBC: DB 기반. 비즈니스 트랜잭션과 같은 트랜잭션으로 묶어 강한 멱등성 보장.
     *       결제처럼 절대 중복 실행이 안 되는 경우에 권장.
     */
    private final StorageType storage;

    /**
     * 저장 형식의 버전. 배포로 응답 DTO 필드가 바뀐 경우 이 값을 올리면
     * 기존에 저장된 응답을 캐시 미스로 처리해서 재실행한다.
     * DTO 구조 변경이 없는 배포에서는 올릴 필요 없다.
     */
    private final int schemaVersion;

    public IdempotencyProperties(
            @DefaultValue("idempotency:") String keyPrefix,
            @DefaultValue("24") long ttlHours,
            @DefaultValue("30") long inProgressTtlSeconds,
            @DefaultValue("FAIL_CLOSED") ClaimFailurePolicy onClaimFailure,
            @DefaultValue("REDIS") StorageType storage,
            @DefaultValue("1") int schemaVersion) {
        this.keyPrefix = keyPrefix;
        this.ttlHours = ttlHours;
        this.inProgressTtlSeconds = inProgressTtlSeconds;
        this.onClaimFailure = onClaimFailure;
        this.storage = storage;
        this.schemaVersion = schemaVersion;
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

    public ClaimFailurePolicy getOnClaimFailure() {
        return onClaimFailure;
    }

    public StorageType getStorage() {
        return storage;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }
}
