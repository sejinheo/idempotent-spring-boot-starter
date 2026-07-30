package com.github.sejinheo.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sejinheo.idempotent.exception.IdempotencyStorageException;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis 장애 시 동작 정책(FAIL_OPEN / FAIL_CLOSED) 테스트.
 *
 * 실제 Redis 없이 IdempotencyStorage mock 빈으로 장애를 시뮬레이션한다.
 */
class StorageFailurePolicyTest {

    record TestRequest(String name) {}
    record TestResponse(String result) {}

    @RestController
    @RequestMapping("/policy-test")
    static class PolicyTestController {

        static final AtomicInteger callCount = new AtomicInteger();

        @Idempotent
        @PostMapping("/dto")
        public ResponseEntity<TestResponse> dto(@RequestBody TestRequest request) {
            callCount.incrementAndGet();
            return ResponseEntity.ok(new TestResponse(request.name()));
        }
    }

    // -----------------------------------------------------------------------
    // FAIL_CLOSED (기본값) — tryClaim 예외 시 요청이 실패해야 한다
    // -----------------------------------------------------------------------

    @SpringBootTest(classes = FailClosedConfig.class)
    @AutoConfigureMockMvc
    @TestPropertySource(properties = "idempotency.on-storage-failure=FAIL_CLOSED")
    static class FailClosedTest {

        @Autowired
        MockMvc mockMvc;

        @BeforeEach
        void resetCount() {
            PolicyTestController.callCount.set(0);
        }

        @Test
        void Redis_장애시_FAIL_CLOSED이면_요청이_실패한다() throws Exception {
            mockMvc.perform(post("/policy-test/dto")
                            .header("Idempotency-Key", "key-fail-closed")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"apple"}
                                    """))
                    .andExpect(status().is5xxServerError());

            assertThat(PolicyTestController.callCount.get()).isEqualTo(0);
        }
    }

    // -----------------------------------------------------------------------
    // FAIL_OPEN — tryClaim 예외 시 요청이 통과되어야 한다
    // -----------------------------------------------------------------------

    @SpringBootTest(classes = FailOpenConfig.class)
    @AutoConfigureMockMvc
    @TestPropertySource(properties = "idempotency.on-storage-failure=FAIL_OPEN")
    static class FailOpenTryClaimTest {

        @Autowired
        MockMvc mockMvc;

        @BeforeEach
        void resetCount() {
            PolicyTestController.callCount.set(0);
        }

        @Test
        void Redis_장애시_FAIL_OPEN이면_요청이_통과된다() throws Exception {
            mockMvc.perform(post("/policy-test/dto")
                            .header("Idempotency-Key", "key-fail-open")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"apple"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("apple"));

            assertThat(PolicyTestController.callCount.get()).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // FAIL_OPEN — complete 예외 시에도 result가 반환되어야 한다
    // -----------------------------------------------------------------------

    @SpringBootTest(classes = FailOpenCompleteFailConfig.class)
    @AutoConfigureMockMvc
    @TestPropertySource(properties = "idempotency.on-storage-failure=FAIL_OPEN")
    static class FailOpenCompleteFailTest {

        @Autowired
        MockMvc mockMvc;

        @BeforeEach
        void resetCount() {
            PolicyTestController.callCount.set(0);
        }

        @Test
        void FAIL_OPEN에서_complete_실패해도_result를_반환한다() throws Exception {
            mockMvc.perform(post("/policy-test/dto")
                            .header("Idempotency-Key", "key-complete-fail")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"apple"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("apple"));

            assertThat(PolicyTestController.callCount.get()).isEqualTo(1);
        }
    }

    // -----------------------------------------------------------------------
    // Spring 설정 — tryClaim에서 예외를 던지는 mock storage
    // -----------------------------------------------------------------------

    @Configuration
    @EnableAutoConfiguration
    static class FailClosedConfig {

        @Bean
        PolicyTestController policyTestController() {
            return new PolicyTestController();
        }

        @Bean
        @Primary
        IdempotencyStorage brokenStorage() {
            return new IdempotencyStorage() {
                @Override
                public Optional<IdempotencyResult> find(String key) {
                    throw new IdempotencyStorageException("Redis 장애 시뮬레이션", null);
                }

                @Override
                public boolean tryClaim(String key, IdempotencyResult inProgress, Duration ttl) {
                    throw new IdempotencyStorageException("Redis 장애 시뮬레이션", null);
                }

                @Override
                public void complete(String key, IdempotencyResult completed, Duration ttl) {
                    throw new IdempotencyStorageException("Redis 장애 시뮬레이션", null);
                }

                @Override
                public void release(String key) {
                    throw new IdempotencyStorageException("Redis 장애 시뮬레이션", null);
                }
            };
        }
    }

    @Configuration
    @EnableAutoConfiguration
    static class FailOpenConfig {

        @Bean
        PolicyTestController policyTestController() {
            return new PolicyTestController();
        }

        @Bean
        @Primary
        IdempotencyStorage brokenStorage() {
            return new IdempotencyStorage() {
                @Override
                public Optional<IdempotencyResult> find(String key) {
                    throw new IdempotencyStorageException("Redis 장애 시뮬레이션", null);
                }

                @Override
                public boolean tryClaim(String key, IdempotencyResult inProgress, Duration ttl) {
                    throw new IdempotencyStorageException("Redis 장애 시뮬레이션", null);
                }

                @Override
                public void complete(String key, IdempotencyResult completed, Duration ttl) {
                    throw new IdempotencyStorageException("Redis 장애 시뮬레이션", null);
                }

                @Override
                public void release(String key) {
                    throw new IdempotencyStorageException("Redis 장애 시뮬레이션", null);
                }
            };
        }
    }

    // complete()만 예외, tryClaim()은 성공하는 mock storage
    @Configuration
    @EnableAutoConfiguration
    static class FailOpenCompleteFailConfig {

        @Bean
        PolicyTestController policyTestController() {
            return new PolicyTestController();
        }

        @Bean
        @Primary
        IdempotencyStorage completeFailStorage() {
            return new IdempotencyStorage() {
                private final java.util.concurrent.ConcurrentHashMap<String, IdempotencyResult> store
                        = new java.util.concurrent.ConcurrentHashMap<>();

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
                    throw new IdempotencyStorageException("complete Redis 장애 시뮬레이션", null);
                }

                @Override
                public void release(String key) {
                    store.remove(key);
                }
            };
        }
    }
}
