CREATE TABLE IF NOT EXISTS payments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id     VARCHAR(36)    NOT NULL UNIQUE,
    order_id       VARCHAR(36)    NOT NULL UNIQUE,
    user_id        VARCHAR(64)    NOT NULL,
    amount         DECIMAL(12,2)  NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    created_at     DATETIME       NOT NULL,
    updated_at     DATETIME       NOT NULL
);
