package com.github.sejinheo.idempotent.config;

/**
 * 저장소 선점(tryClaim) 실패 시 동작 정책.
 *
 * FAIL_CLOSED: 예외를 그대로 던져서 요청을 실패 처리한다 (기본값).
 * FAIL_OPEN:   예외를 무시하고 요청을 통과시킨다. 중복 실행 가능성을 감수한다.
 *
 * 참고: 결과 저장(complete) 실패는 이 정책과 무관하게 항상 결과를 반환하고
 * WARN 로그를 남긴다. 비즈니스 로직이 이미 성공한 상태에서 저장 실패를 이유로
 * 클라이언트에게 500을 반환하면, 클라이언트가 재시도 시 중복 실행이 발생한다.
 *
 * 설정 예:
 * idempotency:
 *   on-claim-failure: FAIL_OPEN
 */
public enum ClaimFailurePolicy {
    FAIL_CLOSED,
    FAIL_OPEN
}
