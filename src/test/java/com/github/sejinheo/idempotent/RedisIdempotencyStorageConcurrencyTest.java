package com.github.sejinheo.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sejinheo.idempotent.storage.IdempotencyResult;
import com.github.sejinheo.idempotent.storage.IdempotencyStorage;
import com.github.sejinheo.idempotent.storage.RedisIdempotencyStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 라이브러리에서 가장 중요한 테스트.
 * "같은 키로 동시에 여러 요청이 와도 딱 하나만 선점에 성공하는가"를 검증한다.
 * 이게 깨지면 멱등성 라이브러리로서 존재 의미가 없다.
 */
@Testcontainers
class RedisIdempotencyStorageConcurrencyTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private IdempotencyStorage storage;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory =
                new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        storage = new RedisIdempotencyStorage(redisTemplate, new ObjectMapper());
    }

    @Test
    void 동시에_같은_키로_요청하면_하나만_선점에_성공한다() throws InterruptedException {
        String key = "idempotency:test-key";
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    boolean claimed = storage.tryClaim(
                            key, IdempotencyResult.inProgress("same-hash"), Duration.ofSeconds(30));
                    if (claimed) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
    }
}
