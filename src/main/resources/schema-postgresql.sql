CREATE TABLE IF NOT EXISTS idempotency_keys
(
    idempotency_key VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    body_hash       VARCHAR(64)  NOT NULL,
    http_status     INT,
    body_json       TEXT,
    expires_at      TIMESTAMP    NOT NULL,
    PRIMARY KEY (idempotency_key)
);
