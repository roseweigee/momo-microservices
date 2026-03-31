CREATE TABLE IF NOT EXISTS warehouses (
    warehouse_id VARCHAR(32)  NOT NULL PRIMARY KEY,
    name         VARCHAR(64)  NOT NULL,
    city         VARCHAR(20),
    latitude     DOUBLE,
    longitude    DOUBLE,
    type         VARCHAR(20)  NOT NULL DEFAULT 'SATELLITE'
);

CREATE TABLE IF NOT EXISTS warehouse_stock (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id VARCHAR(32)    NOT NULL,
    product_id   VARCHAR(64)    NOT NULL,
    quantity     INT            NOT NULL DEFAULT 0,
    updated_at   DATETIME       NOT NULL,
    UNIQUE KEY uk_wh_prod (warehouse_id, product_id)
);

CREATE TABLE IF NOT EXISTS batch_records (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id     VARCHAR(64)    NOT NULL UNIQUE,
    product_id   VARCHAR(64)    NOT NULL,
    warehouse_id VARCHAR(32)    NOT NULL,
    location     VARCHAR(32)    NOT NULL,
    expire_date  DATE,
    quantity     INT            NOT NULL DEFAULT 0,
    received_at  DATETIME       NOT NULL,
    updated_at   DATETIME       NOT NULL,
    INDEX idx_product_warehouse (product_id, warehouse_id),
    INDEX idx_expire_date (expire_date)
);

-- 倉庫種子資料（台灣主要城市）
INSERT INTO warehouses VALUES
('WH-MAIN-TAOYUAN', '桃園主倉',   '桃園', 25.0138, 121.2145, 'MAIN'),
('WH-SAT-TAIPEI',   '台北衛星倉', '台北', 25.0330, 121.5654, 'SATELLITE'),
('WH-SAT-XINDIAN',  '新店衛星倉', '新北', 24.9675, 121.5413, 'SATELLITE'),
('WH-SAT-TAICHUNG', '台中衛星倉', '台中', 24.1477, 120.6736, 'SATELLITE'),
('WH-SAT-KAOHSIUNG','高雄衛星倉', '高雄', 22.6273, 120.3014, 'SATELLITE')
ON DUPLICATE KEY UPDATE warehouse_id = warehouse_id;

-- 庫存種子資料
INSERT INTO warehouse_stock (warehouse_id, product_id, quantity, updated_at) VALUES
('WH-MAIN-TAOYUAN',  'prod-001', 500, NOW()),
('WH-MAIN-TAOYUAN',  'prod-002', 800, NOW()),
('WH-MAIN-TAOYUAN',  'prod-003', 200, NOW()),
('WH-SAT-TAIPEI',    'prod-001',  50, NOW()),
('WH-SAT-TAIPEI',    'prod-002', 100, NOW()),
('WH-SAT-XINDIAN',   'prod-001',  30, NOW()),
('WH-SAT-TAICHUNG',  'prod-001',  40, NOW()),
('WH-SAT-KAOHSIUNG', 'prod-001',  20, NOW())
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

-- 批號種子資料（含效期，測試 FEFO）
INSERT INTO batch_records (batch_id, product_id, warehouse_id, location, expire_date, quantity, received_at, updated_at) VALUES
('BATCH-001', 'prod-001', 'WH-SAT-TAIPEI', 'A-1-01', '2024-06-30', 10, NOW(), NOW()),
('BATCH-002', 'prod-001', 'WH-SAT-TAIPEI', 'A-1-02', '2024-12-31', 20, NOW(), NOW()),
('BATCH-003', 'prod-001', 'WH-SAT-TAIPEI', 'A-1-03', '2025-06-30', 20, NOW(), NOW()),
('BATCH-004', 'prod-002', 'WH-SAT-TAIPEI', 'B-2-01', NULL,         50, NOW(), NOW()),
('BATCH-005', 'prod-001', 'WH-MAIN-TAOYUAN','C-3-01','2025-03-01', 100,NOW(), NOW())
ON DUPLICATE KEY UPDATE batch_id = batch_id;
