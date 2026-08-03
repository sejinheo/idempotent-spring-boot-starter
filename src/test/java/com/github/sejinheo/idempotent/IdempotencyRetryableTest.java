package com.github.sejinheo.idempotent;

import com.github.sejinheo.idempotent.annotation.Idempotent;
import com.github.sejinheo.idempotent.exception.IdempotencyRetryable;
import com.github.sejinheo.idempotent.spi.UserIdExtractor;
import com.github.sejinheo.idempotent.storage.IdempotencyResult;
import com.github.sejinheo.idempotent.storage.IdempotencyStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IdempotencyRetryable 마커 인터페이스 동작 검증.
 *
 * - IdempotencyRetryable 구현 예외 발생 시 키 삭제 → 재시도 허용
 * - 일반 예외 발생 시 키 유지 → 재시도 시 409
 */
@SpringBootTest(classes = IdempotencyRetryableTest.RetryableConfig.class)
@AutoConfigureMockMvc
class IdempotencyRetryableTest {

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void reset() {
        RetryableConfig.InMemoryStorage.store.clear();
        RetryableController.callCount.set(0);
        RetryableController.shouldThrowRetryable.set(false);
        RetryableController.shouldThrowNonRetryable.set(false);
    }

    // -----------------------------------------------------------------------
    // 케이스 1: IdempotencyRetryable 구현 예외 → 키 삭제 → 재시도 시 실행
    // -----------------------------------------------------------------------

    @Test
    void IdempotencyRetryable_예외_발생시_키가_삭제되어_재시도가_허용된다() throws Exception {
        RetryableController.shouldThrowRetryable.set(true);

        // 첫 요청 → RetryableException 발생 → 키 삭제
        mockMvc.perform(post("/retryable-test/pay")
                        .header("Idempotency-Key", "key-retryable")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000}
                                """))
                .andExpect(status().is5xxServerError());

        assertThat(RetryableController.callCount.get()).isEqualTo(1);

        // 재시도 → 키가 없으므로 다시 실행
        mockMvc.perform(post("/retryable-test/pay")
                        .header("Idempotency-Key", "key-retryable")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000}
                                """))
                .andExpect(status().is5xxServerError());

        // 키가 삭제됐으므로 2번 실행
        assertThat(RetryableController.callCount.get()).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // 케이스 2: 일반 예외 → 키 유지 → 재시도 시 409
    // -----------------------------------------------------------------------

    @Test
    void 일반_예외_발생시_키가_유지되어_재시도가_막힌다() throws Exception {
        RetryableController.shouldThrowNonRetryable.set(true);

        // 첫 요청 → NonRetryableException 발생 → 키 유지
        mockMvc.perform(post("/retryable-test/pay")
                        .header("Idempotency-Key", "key-nonretryable")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000}
                                """))
                .andExpect(status().is5xxServerError());

        assertThat(RetryableController.callCount.get()).isEqualTo(1);

        // 재시도 → 키가 남아있으므로 IN_PROGRESS → 409
        mockMvc.perform(post("/retryable-test/pay")
                        .header("Idempotency-Key", "key-nonretryable")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000}
                                """))
                .andExpect(status().isConflict());

        // 메서드는 1번만 실행
        assertThat(RetryableController.callCount.get()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // 테스트용 예외
    // -----------------------------------------------------------------------

    static class RetryableException extends RuntimeException
            implements IdempotencyRetryable {
        RetryableException() { super("재시도 가능한 예외"); }
    }

    static class NonRetryableException extends RuntimeException {
        NonRetryableException() { super("재시도 불가 예외"); }
    }

    // -----------------------------------------------------------------------
    // 테스트용 컨트롤러
    // -----------------------------------------------------------------------

    record PayRequest(int amount) {}
    record PayResponse(String result) {}

    @RestController
    @RequestMapping("/retryable-test")
    static class RetryableController {

        static final AtomicInteger callCount = new AtomicInteger();
        static final AtomicBoolean shouldThrowRetryable = new AtomicBoolean(false);
        static final AtomicBoolean shouldThrowNonRetryable = new AtomicBoolean(false);

        @Idempotent
        @PostMapping("/pay")
        public ResponseEntity<PayResponse> pay(@RequestBody PayRequest request) {
            callCount.incrementAndGet();
            if (shouldThrowRetryable.get()) throw new RetryableException();
            if (shouldThrowNonRetryable.get()) throw new NonRetryableException();
            return ResponseEntity.ok(new PayResponse("success"));
        }

        @ExceptionHandler({RetryableException.class, NonRetryableException.class})
        public ResponseEntity<Void> handleException(RuntimeException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // -----------------------------------------------------------------------
    // 테스트용 설정 — in-memory storage로 Docker 없이 실행
    // -----------------------------------------------------------------------

    @Configuration
    @EnableAutoConfiguration
    static class RetryableConfig {

        @Bean
        RetryableController retryableController() {
            return new RetryableController();
        }

        @Bean
        UserIdExtractor userIdExtractor() {
            return request -> request.getHeader("X-User-Id");
        }

        @Bean
        @Primary
        IdempotencyStorage inMemoryStorage() {
            return new InMemoryStorage();
        }

        static class InMemoryStorage implements IdempotencyStorage {
            static final ConcurrentHashMap<String, IdempotencyResult> store = new ConcurrentHashMap<>();

            @Override
            public Optional<IdempotencyResult> find(String key) {
                return Optional.ofNullable(store.get(key));
            }

            @Override
            public boolean tryClaim(String key, IdempotencyResult inProgress, Duration ttl) {
                return store.putIfAbsent(key, inProgress) == null;
            }

            @Override
            public void complete(String key, IdempotencyResult completed, Duration ttl) {
                store.put(key, completed);
            }

            @Override
            public void release(String key) {
                store.remove(key);
            }
        }
    }
}
