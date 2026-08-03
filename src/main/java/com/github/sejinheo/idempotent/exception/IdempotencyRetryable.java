package com.github.sejinheo.idempotent.exception;

/**
 * 이 인터페이스를 구현한 예외가 발생하면 Idempotency 키를 삭제하여 재시도를 허용한다.
 *
 * <p>기본적으로 예외 발생 시 키는 유지된다. 외부 시스템 호출이 이미 성공한 상태에서
 * 후처리 코드가 실패한 경우 키를 삭제하면 재시도 시 이중 실행이 발생할 수 있기 때문이다.</p>
 *
 * <p>재시도해도 안전한 예외(잔액 부족, 입력값 검증 실패 등)에만 구현한다.</p>
 *
 * <pre>{@code
 * // 재시도 허용 — 잔액 부족은 외부 시스템 호출 전에 실패하므로 안전
 * public class InsufficientBalanceException extends RuntimeException
 *         implements IdempotencyRetryable { ... }
 *
 * // 재시도 불허 — 결제는 이미 나갔을 수 있으므로 키를 유지해 이중 결제 방지
 * public class PaymentPostProcessingException extends RuntimeException { ... }
 * }</pre>
 */
public interface IdempotencyRetryable {
}
