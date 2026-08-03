package com.github.sejinheo.idempotent;

import com.github.sejinheo.idempotent.annotation.Idempotent;
import com.github.sejinheo.idempotent.spi.UserIdExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 같은 키로 100개 동시 HTTP 요청을 던져서 실제 메서드가 정확히 1번만 실행되는지 검증.
 * storage 레벨이 아닌 HTTP 요청 레벨에서 멱등성을 보장하는지 확인한다.
 */
@Testcontainers
@SpringBootTest(classes = ConcurrencyIntegrationTest.TestConfig.class)
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        TestController.callCount.set(0);
    }

    @Test
    void 같은_키로_100개_동시_요청시_메서드는_정확히_1번만_실행된다() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    mockMvc.perform(post("/concurrency-test/dto")
                            .header("Idempotency-Key", "concurrent-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"apple"}
                                    """));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(TestController.callCount.get()).isEqualTo(1);
    }

    record TestRequest(String name) {}
    record TestResponse(String result) {}

    @RestController
    @RequestMapping("/concurrency-test")
    static class TestController {

        static final AtomicInteger callCount = new AtomicInteger();

        @Idempotent
        @PostMapping("/dto")
        public TestResponse dto(@RequestBody TestRequest request) {
            callCount.incrementAndGet();
            return new TestResponse(request.name());
        }
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {

        @Bean
        TestController testController() {
            return new TestController();
        }

        @Bean
        LettuceConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        }

        @Bean
        UserIdExtractor userIdExtractor() {
            return request -> "test-user";
        }
    }
}
