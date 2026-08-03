package com.github.sejinheo.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sejinheo.idempotent.annotation.Idempotent;
import com.github.sejinheo.idempotent.spi.UserIdExtractor;
import com.github.sejinheo.idempotent.storage.IdempotencyResult;
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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 저장 형식 버전(schemaVersion) 불일치 시 캐시 미스로 처리해서 재실행하는지 검증.
 */
@Testcontainers
@SpringBootTest(classes = SchemaVersionTest.TestConfig.class)
@AutoConfigureMockMvc
class SchemaVersionTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        TestController.callCount.set(0);
    }

    @Test
    void schemaVersion_불일치시_캐시_무시하고_재실행한다() throws Exception {
        String body = """
                {"name":"apple"}
                """;

        // 첫 요청 → 정상 실행
        mockMvc.perform(post("/schema-test/dto")
                        .header("Idempotency-Key", "key-schema")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        assertThat(TestController.callCount.get()).isEqualTo(1);

        // Redis에 저장된 값의 schemaVersion을 구버전(0)으로 변조
        String redisKey = "idempotency:test-user:key-schema";
        String stored = redisTemplate.opsForValue().get(redisKey);
        String tampered = stored.replace(
                "\"schemaVersion\":" + IdempotencyResult.CURRENT_SCHEMA_VERSION,
                "\"schemaVersion\":0"
        );
        redisTemplate.opsForValue().set(redisKey, tampered, Duration.ofHours(24));

        // 같은 키로 재요청 → schemaVersion 불일치 → 재실행
        mockMvc.perform(post("/schema-test/dto")
                        .header("Idempotency-Key", "key-schema")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        assertThat(TestController.callCount.get()).isEqualTo(2);
    }

    record TestRequest(String name) {}
    record TestResponse(String result) {}

    @RestController
    @RequestMapping("/schema-test")
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
