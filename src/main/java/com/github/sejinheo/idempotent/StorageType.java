package com.github.sejinheo.idempotent;

/**
 * 멱등성 저장소 구현체 선택.
 *
 * idempotency:
 *   storage: redis   # 기본값. Redis SET NX 기반 원자적 선점. 분산 환경에 적합.
 *   storage: jdbc    # DB 기반. 비즈니스 트랜잭션과 같은 트랜잭션으로 묶어 강한 멱등성 보장.
 *                    # 결제처럼 절대 중복 실행이 안 되는 경우에 권장.
 */
public enum StorageType {
    REDIS,
    JDBC
}
