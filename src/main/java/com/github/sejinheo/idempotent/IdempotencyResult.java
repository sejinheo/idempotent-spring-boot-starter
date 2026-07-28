package com.github.sejinheo.idempotent;

/**
 * Redis 키 하나에 저장되는 멱등성 처리 상태.
 *
 * 상태 전이:
 *   (없음) --tryClaim 성공--> IN_PROGRESS --complete--> COMPLETED
 *   IN_PROGRESS 상태에서 예외 발생 시 release()로 삭제되어 다시 (없음)으로 돌아간다.
 *
 * IN_PROGRESS인 동안 같은 키로 요청이 오면 REJECT(409),
 * COMPLETED가 되면 저장된 응답을 그대로 재생(replay)한다.
 * 두 경우 모두 bodyHash가 기존 요청과 다르면 키 재사용 충돌(422)로 처리한다.
 */
public class IdempotencyResult {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    private Status status;

    /**
     * 요청 바디를 SHA-256으로 해싱한 값.
     * Idempotency-Key는 같은데 바디가 다르면 클라이언트가 키를 잘못
     * 재사용한 것으로 보고 캐시 응답을 재생하지 않고 예외를 던진다.
     */
    private String bodyHash;

    /** 응답 상태 코드 (COMPLETED일 때만 유효) */
    private Integer httpStatus;

    /** 응답 바디를 JSON 문자열로 직렬화한 값 (COMPLETED일 때만 유효) */
    private String bodyJson;

    /** 응답 바디의 실제 클래스명. 재생 시 이 타입으로 역직렬화한다. */
    private String bodyClassName;

    public IdempotencyResult() {
    }

    public static IdempotencyResult inProgress(String bodyHash) {
        IdempotencyResult result = new IdempotencyResult();
        result.status = Status.IN_PROGRESS;
        result.bodyHash = bodyHash;
        return result;
    }

    public IdempotencyResult complete(int httpStatus, String bodyJson, String bodyClassName) {
        this.status = Status.COMPLETED;
        this.httpStatus = httpStatus;
        this.bodyJson = bodyJson;
        this.bodyClassName = bodyClassName;
        return this;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getBodyHash() {
        return bodyHash;
    }

    public void setBodyHash(String bodyHash) {
        this.bodyHash = bodyHash;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getBodyJson() {
        return bodyJson;
    }

    public void setBodyJson(String bodyJson) {
        this.bodyJson = bodyJson;
    }

    public String getBodyClassName() {
        return bodyClassName;
    }

    public void setBodyClassName(String bodyClassName) {
        this.bodyClassName = bodyClassName;
    }
}
