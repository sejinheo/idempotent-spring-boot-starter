package com.github.sejinheo.idempotent;

import com.github.sejinheo.idempotent.exception.IdempotencyConflictException;
import com.github.sejinheo.idempotent.exception.IdempotencyKeyMissingException;
import com.github.sejinheo.idempotent.exception.IdempotencyKeyReuseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * {@literal @}Idempotent 가 붙은 컨트롤러 메서드를 가로채서 멱등성을 보장하는 Aspect.
 *
 * 처리 흐름:
 * 1. Idempotency-Key 헤더를 읽는다. 없으면 400.
 * 2. 요청 바디를 해싱한다.
 * 3. Redis에 (key -> IN_PROGRESS) 를 원자적으로 선점 시도한다.
 *    - 성공: 실제 메서드를 실행하고 결과를 COMPLETED로 저장한다.
 *    - 실패(이미 키가 존재): 기존 상태를 조회해서
 *        - bodyHash가 다르면 키 재사용 충돌(422)
 *        - IN_PROGRESS면 동시 요청이므로 REJECT(409)
 *        - COMPLETED면 저장된 응답을 그대로 재생
 * 4. 메서드 실행 중 예외가 나면 키를 삭제해서 재시도를 허용한다.
 *
 * ResponseEntity 지원: 제네릭 타입 파라미터를 런타임에 추출해서 역직렬화하므로
 * ResponseEntity&lt;Void&gt;, ResponseEntity&lt;SomeDto&gt;, ResponseEntity&lt;List&lt;SomeDto&gt;&gt; 등
 * 모든 ResponseEntity 반환 타입과 순수 DTO 반환 타입을 모두 지원한다.
 */
@Aspect
public class HttpIdempotentAspect {

    private final IdempotencyStorage storage;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;

    public HttpIdempotentAspect(IdempotencyStorage storage,
                                 IdempotencyProperties properties,
                                 ObjectMapper objectMapper) {
        this.storage = storage;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = currentRequest();
        String headerKey = request.getHeader(idempotent.headerName());
        if (headerKey == null || headerKey.isBlank()) {
            throw new IdempotencyKeyMissingException(idempotent.headerName());
        }

        String redisKey = properties.getKeyPrefix() + headerKey;
        Duration ttl = Duration.ofHours(properties.getTtlHours());

        Object requestBody = findRequestBodyArgument(joinPoint);
        String bodyHash = hash(requestBody);

        boolean claimed = storage.tryClaim(redisKey, IdempotencyResult.inProgress(bodyHash), ttl);

        if (!claimed) {
            IdempotencyResult existing = storage.find(redisKey)
                    .orElseThrow(() -> new IdempotencyConflictException("동시 요청 처리 중 상태 조회에 실패했습니다."));

            if (!bodyHash.equals(existing.getBodyHash())) {
                // 같은 키인데 바디가 다름 -> 클라이언트가 키를 잘못 재사용한 것으로 판단
                throw new IdempotencyKeyReuseException(headerKey);
            }

            if (existing.getStatus() == IdempotencyResult.Status.IN_PROGRESS) {
                // 첫 요청이 아직 처리 중 -> REJECT
                throw new IdempotencyConflictException("이미 처리 중인 요청입니다. 잠시 후 다시 시도해주세요.");
            }

            // COMPLETED -> 저장된 응답을 그대로 재생
            return replay(existing, joinPoint);
        }

        try {
            Object result = joinPoint.proceed();
            IdempotencyResult completed = new IdempotencyResult();
            completed.setBodyHash(bodyHash);
            if (result instanceof ResponseEntity<?> responseEntity) {
                int statusCode = responseEntity.getStatusCode().value();
                Object body = responseEntity.getBody();
                String bodyJson = (body == null) ? null : objectMapper.writeValueAsString(body);
                String bodyClassName = (body == null) ? null : body.getClass().getName();
                completed.complete(statusCode, bodyJson, bodyClassName);
            } else {
                String bodyJson = (result == null) ? null : objectMapper.writeValueAsString(result);
                String bodyClassName = (result == null) ? null : result.getClass().getName();
                completed.complete(200, bodyJson, bodyClassName);
            }
            storage.complete(redisKey, completed, ttl);
            return result;
        } catch (Throwable ex) {
            // 실패한 요청은 캐싱하지 않고 재시도를 허용한다
            storage.release(redisKey);
            throw ex;
        }
    }

    private Object replay(IdempotencyResult existing, ProceedingJoinPoint joinPoint) throws Exception {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        if (ResponseEntity.class.isAssignableFrom(method.getReturnType())) {
            if (existing.getBodyJson() == null) {
                return ResponseEntity.status(existing.getHttpStatus()).build();
            }
            Type genericReturnType = method.getGenericReturnType();
            if (genericReturnType instanceof ParameterizedType pt) {
                Type bodyType = pt.getActualTypeArguments()[0];
                Object body = objectMapper.readValue(existing.getBodyJson(),
                        objectMapper.constructType(bodyType));
                return ResponseEntity.status(existing.getHttpStatus()).body(body);
            }
            return ResponseEntity.status(existing.getHttpStatus()).build();
        }

        if (existing.getBodyJson() == null) {
            return null;
        }
        return objectMapper.readValue(existing.getBodyJson(), method.getReturnType());
    }

    /**
     * @RequestBody 가 붙은 파라미터를 찾아서 해싱 대상으로 사용한다.
     * 없으면 전체 인자 배열을 해싱 대상으로 삼는다 (경로/쿼리 파라미터만 있는 API 등).
     */
    private Object findRequestBodyArgument(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = signature.getMethod().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(RequestBody.class)) {
                return args[i];
            }
        }
        return args;
    }

    private String hash(Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new IllegalStateException("요청 바디 해싱에 실패했습니다.", e);
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("HTTP 요청 컨텍스트 밖에서는 @Idempotent를 사용할 수 없습니다.");
        }
        return attrs.getRequest();
    }
}
