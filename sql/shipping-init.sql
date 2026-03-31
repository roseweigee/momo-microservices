CREATE TABLE IF NOT EXISTS shipments (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    shipment_id  VARCHAR(36)  NOT NULL UNIQUE,
    order_id     VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(64)  NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PREPARING',
    tracking_no  VARCHAR(64),
    address      TEXT,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL
);
