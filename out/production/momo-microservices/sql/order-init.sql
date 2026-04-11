CREATE TABLE IF NOT EXISTS orders (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    VARCHAR(36)    NOT NULL UNIQUE,
    user_id     VARCHAR(64)    NOT NULL,
    product_id  VARCHAR(64)    NOT NULL,
    quantity    INT            NOT NULL,
    total_price DECIMAL(12, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at  DATETIME       NOT NULL,
    updated_at  DATETIME       NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id   VARCHAR(64)    NOT NULL UNIQUE,
    product_name VARCHAR(128)   NOT NULL,
    price        DECIMAL(12, 2) NOT NULL,
    stock        INT            NOT NULL
);

INSERT INTO products (product_id, product_name, price, stock) VALUES
('prod-001', 'iPhone 15 Pro', 35900, 100),
('prod-002', 'AirPods Pro',    7990, 200),
('prod-003', 'MacBook Air M3', 42900,  50)
ON DUPLICATE KEY UPDATE product_id = product_id;

CREATE TABLE IF NOT EXISTS saga_state (
    order_id       VARCHAR(36)  NOT NULL PRIMARY KEY,
    status         VARCHAR(30)  NOT NULL,
    current_step   VARCHAR(30),
    failure_reason TEXT,
    product_id     VARCHAR(64),
    quantity       INT,
    payment_id     VARCHAR(36),
    shipment_id    VARCHAR(36),
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL
);
