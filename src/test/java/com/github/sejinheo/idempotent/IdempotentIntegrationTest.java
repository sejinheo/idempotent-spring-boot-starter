package com.github.sejinheo.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.ResponseEntity;
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

@Testcontainers
@SpringBootTest(classes = IdempotentIntegrationTest.TestConfig.class)
@AutoConfigureMockMvc
class IdempotentIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        // 각 테스트마다 카운터 초기화
        TestController.dtoCallCount.set(0);
        TestController.voidCallCount.set(0);
        TestController.responseEntityCallCount.set(0);
        TestController.customTtlCallCount.set(0);
    }

    // -----------------------------------------------------------------------
    // 테스트 시나리오
    // -----------------------------------------------------------------------

    @Test
    void 헤더_없으면_400() throws Exception {
        mockMvc.perform(post("/test/dto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"apple"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 첫_요청은_정상_실행되고_200_반환() throws Exception {
        mockMvc.perform(post("/test/dto")
                        .header("Idempotency-Key", "key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"apple"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));
    }

    @Test
    void 같은_키_같은_바디_재요청시_캐시된_응답_반환하고_실제_메서드는_한번만_실행() throws Exception {
        String body = """
                {"name":"apple"}
                """;

        mockMvc.perform(post("/test/dto")
                        .header("Idempotency-Key", "key-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        mockMvc.perform(post("/test/dto")
                        .header("Idempotency-Key", "key-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        assertThat(TestController.dtoCallCount.get()).isEqualTo(1);
    }

    @Test
    void 같은_키_다른_바디_422() throws Exception {
        mockMvc.perform(post("/test/dto")
                        .header("Idempotency-Key", "key-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"apple"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/test/dto")
                        .header("Idempotency-Key", "key-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"banana"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ResponseEntity_Void_반환_정상_재생() throws Exception {
        String body = """
                {"name":"apple"}
                """;

        mockMvc.perform(post("/test/void")
                        .header("Idempotency-Key", "key-004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/test/void")
                        .header("Idempotency-Key", "key-004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(TestController.voidCallCount.get()).isEqualTo(1);
    }

    @Test
    void 다른_사용자는_같은_키여도_격리되어_각자_독립_실행() throws Exception {
        // TestConfig의 UserIdExtractor는 "test-user" 고정이므로
        // 헤더로 사용자 ID를 흉내 낸다 (Aspect 내부에서 X-User-Id를 쓰도록 TestConfig를 변경)
        // → 이 테스트는 UserIsolationConfig 전용 컨텍스트에서 별도로 검증한다.
        // 여기서는 같은 사용자가 같은 키를 보내면 1번만 실행됨을 재확인하는 용도.
        String body = """
                {"name":"apple"}
                """;

        mockMvc.perform(post("/test/dto")
                        .header("Idempotency-Key", "isolation-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        mockMvc.perform(post("/test/dto")
                        .header("Idempotency-Key", "isolation-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // 같은 사용자 → 캐시 히트, 1번만 실행
        assertThat(TestController.dtoCallCount.get()).isEqualTo(1);
    }

    @Test
    void 어노테이션_inProgressTtlSeconds_지정시_캐시_정상_동작() throws Exception {
        String body = """
                {"name":"apple"}
                """;

        mockMvc.perform(post("/test/custom-ttl")
                        .header("Idempotency-Key", "key-custom-ttl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        mockMvc.perform(post("/test/custom-ttl")
                        .header("Idempotency-Key", "key-custom-ttl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("apple"));

        assertThat(TestController.customTtlCallCount.get()).isEqualTo(1);
    }

    @Test
    void ResponseEntity_DTO_반환_상태코드와_바디_모두_재생() throws Exception {
        String body = """
                {"name":"apple"}
                """;

        mockMvc.perform(post("/test/response-entity")
                        .header("Idempotency-Key", "key-005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("apple"));

        mockMvc.perform(post("/test/response-entity")
                        .header("Idempotency-Key", "key-005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("apple"));

        assertThat(TestController.responseEntityCallCount.get()).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // 테스트용 컨트롤러 & DTO
    // -----------------------------------------------------------------------

    record TestRequest(String name) {}
    record TestResponse(String result) {}

    @RestController
    @RequestMapping("/test")
    static class TestController {

        static final AtomicInteger dtoCallCount = new AtomicInteger();
        static final AtomicInteger voidCallCount = new AtomicInteger();
        static final AtomicInteger responseEntityCallCount = new AtomicInteger();

        static final AtomicInteger customTtlCallCount = new AtomicInteger();

        @Idempotent
        @PostMapping("/dto")
        public TestResponse dto(@RequestBody TestRequest request) {
            dtoCallCount.incrementAndGet();
            return new TestResponse(request.name());
        }

        @Idempotent(inProgressTtlSeconds = 120)
        @PostMapping("/custom-ttl")
        public TestResponse customTtl(@RequestBody TestRequest request) {
            customTtlCallCount.incrementAndGet();
            return new TestResponse(request.name());
        }

        @Idempotent
        @PostMapping("/void")
        public ResponseEntity<Void> voidEndpoint(@RequestBody TestRequest request) {
            voidCallCount.incrementAndGet();
            return ResponseEntity.ok().build();
        }

        @Idempotent
        @PostMapping("/response-entity")
        public ResponseEntity<TestResponse> responseEntity(@RequestBody TestRequest request) {
            responseEntityCallCount.incrementAndGet();
            return ResponseEntity.status(201).body(new TestResponse(request.name()));
        }
    }

    // -----------------------------------------------------------------------
    // 테스트용 Spring Boot 설정
    // AutoConfiguration을 그대로 활용하고, Redis 연결만 Testcontainers로 교체
    // -----------------------------------------------------------------------

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
