package com.github.sejinheo.idempotent;

/**
 * Redis 등 저장소 장애 시 동작 정책.
 *
 * FAIL_CLOSED: 저장소 예외를 그대로 던져서 요청을 실패 처리한다 (기본값).
 * FAIL_OPEN:   저장소 예외를 무시하고 요청을 통과시킨다. 중복 실행 가능성을 감수한다.
 *
 * 어떤 정책을 선택할지는 라이브러리가 아닌 사용하는 쪽이 결정해야 한다.
 *
 * 설정 예:
 * idempotency:
 *   on-storage-failure: FAIL_OPEN
 */
public enum FailurePolicy {
    FAIL_CLOSED,
    FAIL_OPEN
}
