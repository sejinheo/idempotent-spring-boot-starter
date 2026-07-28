package com.github.sejinheo.idempotent;

import java.time.Duration;
import java.util.Optional;

/**
 * 멱등성 상태를 저장하는 저장소 추상화.
 *
 * v1은 Redis 구현체(RedisIdempotencyStorage) 하나만 있지만, 인터페이스로
 * 분리해두면 (1) 단위 테스트에서 mock으로 대체하기 쉽고 (2) 나중에 다른
 * 저장소로 교체하고 싶을 때 이 인터페이스만 새로 구현하면 된다.
 */
public interface IdempotencyStorage {

    /**
     * key에 해당하는 상태를 조회한다. 없으면 Optional.empty().
     */
    Optional<IdempotencyResult> find(String key);

    /**
     * key가 존재하지 않을 때만 IN_PROGRESS 상태로 원자적으로 선점한다
     * (Redis SET NX 기반). 이미 존재하면 false를 반환하며, 이 경우 호출자는
     * find()로 기존 상태를 다시 조회해서 REJECT/재생 여부를 판단해야 한다.
     */
    boolean tryClaim(String key, IdempotencyResult inProgress, Duration ttl);

    /**
     * 처리 완료된 결과로 덮어쓴다. tryClaim에 성공한 요청만 호출해야 한다.
     */
    void complete(String key, IdempotencyResult completed, Duration ttl);

    /**
     * 메서드 실행 중 예외가 발생했을 때 키를 삭제해서 재시도를 허용한다.
     */
    void release(String key);
}
