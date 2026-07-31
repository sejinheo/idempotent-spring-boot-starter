package com.github.sejinheo.idempotent;

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

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 사용자 간 Idempotency-Key 격리 테스트.
 *
 * UserIdExtractor가 X-User-Id 헤더로 사용자를 구분하도록 설정하고,
 * 서로 다른 사용자가 같은 키를 보냈을 때 각자 독립적으로 실행되는지 검증한다.
 */
@Testcontainers
@SpringBootTest(classes = UserIsolationTest.IsolationConfig.class)
@AutoConfigureMockMvc
class UserIsolationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        IsolationController.callCount.set(0);
    }

    @Test
    void 다른_사용자는_같은_키와_바디여도_각자_독립_실행된다() throws Exception {
        String sameKey = "shared-key";
        String sameBody = """
                {"name":"apple"}
                """;

        // 사용자 A 요청
        mockMvc.perform(post("/isolation/dto")
                        .header("Idempotency-Key", sameKey)
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sameBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        // 사용자 B 요청 — 같은 키, 같은 바디지만 다른 사람
        mockMvc.perform(post("/isolation/dto")
                        .header("Idempotency-Key", sameKey)
                        .header("X-User-Id", "user-B")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sameBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        // A와 B가 격리되어 있으므로 실제 메서드가 2번 실행되어야 한다
        assertThat(IsolationController.callCount.get()).isEqualTo(2);
    }

    @Test
    void 같은_사용자는_같은_키로_재요청시_1번만_실행된다() throws Exception {
        String body = """
                {"name":"apple"}
                """;

        mockMvc.perform(post("/isolation/dto")
                        .header("Idempotency-Key", "my-key")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/isolation/dto")
                        .header("Idempotency-Key", "my-key")
                        .header("X-User-Id", "user-A")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // 같은 사용자 → 캐시 히트, 1번만 실행
        assertThat(IsolationController.callCount.get()).isEqualTo(1);
    }

    record TestRequest(String name) {}
    record TestResponse(String result) {}

    @RestController
    @RequestMapping("/isolation")
    static class IsolationController {

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
    static class IsolationConfig {

        @Bean
        IsolationController isolationController() {
            return new IsolationController();
        }

        @Bean
        LettuceConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        }

        // X-User-Id 헤더로 사용자를 구분한다
        @Bean
        UserIdExtractor userIdExtractor() {
            return request -> request.getHeader("X-User-Id");
        }
    }
}
