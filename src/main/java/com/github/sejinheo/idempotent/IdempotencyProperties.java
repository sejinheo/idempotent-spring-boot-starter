package com.github.sejinheo.idempotent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 idempotency.* 프로퍼티와 바인딩된다.
 *
 * 예:
 * idempotency:
 *   key-prefix: "order-service:idempotency:"
 *   ttl-hours: 24
 */
@ConfigurationProperties(prefix = "idempotency")
public class IdempotencyProperties {

    /**
     * Redis에 저장되는 키의 prefix.
     * 여러 MS가 같은 Redis를 공유하는 경우 서비스별로 다르게 설정해서
     * 키 충돌을 막는다. 서비스명을 넣는 걸 권장.
     */
    private String keyPrefix = "idempotency:";

    /**
     * 캐시된 응답의 TTL(시간 단위). v1에서는 전역 하나만 적용하고
     * 어노테이션별 개별 TTL은 지원하지 않는다.
     */
    private long ttlHours = 24;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public long getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(long ttlHours) {
        this.ttlHours = ttlHours;
    }
}
