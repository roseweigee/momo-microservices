# 🔭 Observability 可觀測性設計

> **三大支柱：Metrics · Logs · Tracing**

---

## 架構總覽

```
三個微服務（user / order / shipping）
        │
        ├── 📊 Metrics
        │   Spring Boot Actuator → /actuator/prometheus
        │   Prometheus 每 15s 抓取
        │   Grafana 視覺化儀表板
        │
        ├── 📝 Logs
        │   Logstash JSON 格式（含 traceId/spanId）
        │   Filebeat DaemonSet 收集所有 Pod logs
        │   Logstash 解析 → Elasticsearch 儲存
        │   Kibana 查詢與視覺化
        │
        └── 🔍 Tracing
            OpenTelemetry Agent（micrometer-tracing）
            → Jaeger OTLP Collector（Port 4317）
            → Jaeger UI 查看跨服務 Trace
```

---

## 部署到 K8s

```bash
# 部署所有 Observability 工具
kubectl apply -k k8s/observability/

# 等待啟動（約 2-3 分鐘）
kubectl get pods -n momo-system -w
```

---

## 存取各工具

| 工具 | 用途 | 存取方式 |
|---|---|---|
| **Prometheus** | Metrics 收集 | `kubectl port-forward svc/prometheus 9090:9090 -n momo-system` |
| **Grafana** | Metrics 視覺化 | `kubectl port-forward svc/grafana 3000:3000 -n momo-system` |
| **Kibana** | Log 查詢 | `kubectl port-forward svc/kibana 5601:5601 -n momo-system` |
| **Jaeger** | Distributed Tracing | `kubectl port-forward svc/jaeger 16686:16686 -n momo-system` |

---

## 📊 Metrics（Prometheus + Grafana）

### Spring Boot 暴露的 Metrics

每個服務的 `/actuator/prometheus` 提供：

```
# HTTP 請求數與延遲
http_server_requests_seconds_count{service="order-service", uri="/api/orders"}
http_server_requests_seconds_sum{...}

# JVM 記憶體
jvm_memory_used_bytes{area="heap"}

# 自訂 Kafka lag metrics
kafka_consumer_fetch_manager_records_lag
```

### Grafana 建議 Dashboard

匯入以下 Dashboard ID：
- `4701` — JVM Micrometer（記憶體、GC、Thread）
- `11378` — Spring Boot 2.1 Statistics
- `14430` — Kafka Overview

---

## 📝 Logs（ELK Stack）

### Log 流程

```
Pod stdout（JSON 格式）
    → Filebeat DaemonSet 收集
    → Logstash 解析 + 打 tag
    → Elasticsearch index: momo-logs-{service}-{date}
    → Kibana 查詢
```

### JSON Log 格式（logstash-logback-encoder）

```json
{
  "@timestamp": "2024-03-28T10:00:00.000Z",
  "level": "INFO",
  "service": "order-service",
  "traceId": "abc123def456",
  "spanId": "789xyz",
  "logger": "com.demo.order.service.OrderService",
  "message": "Order saved: ord-ABC123",
  "userId": "usr-001",
  "orderId": "ord-ABC123"
}
```

### Kibana 查詢範例

```
# 查詢 order-service 的錯誤
service: "order-service" AND level: "ERROR"

# 用 traceId 串聯跨服務 log
traceId: "abc123def456"

# 查詢特定訂單的所有 log
orderId: "ord-ABC123"
```

---

## 🔍 Tracing（Jaeger + OpenTelemetry）

### Trace 流程

```
Client → Nginx → order-service
                    │ traceId: abc123
                    ├── 呼叫 user-service（同一個 traceId）
                    ├── Redis 操作（span）
                    ├── MySQL 操作（span）
                    └── Kafka publish（span）
                                │
                         shipping-service
                         （Kafka consumer span，同 traceId）
```

### 重點：跨服務 traceId 傳遞

order-service 呼叫 user-service 時，`traceId` 透過 HTTP Header 自動傳遞：

```
traceparent: 00-abc123def456-789span-01
```

Spring Boot + micrometer-tracing 自動處理，不需要手動寫程式。

### Jaeger UI 使用

1. 打開 `http://localhost:16686`
2. 選擇 Service: `order-service`
3. 點擊 `Find Traces`
4. 點進一個 Trace 看完整的跨服務呼叫鏈

---

## 故障排除範例

### 情境：下單 API 變慢

```
1. Grafana → 看 http_server_requests_seconds P99 上升
2. Jaeger  → 找慢的 Trace，看哪個 Span 耗時最長
3. Kibana  → 用 traceId 找該請求的完整 Log
4. 定位到：Redis Lua Script 執行超時
```

### 情境：庫存扣減異常

```
1. Kibana → 搜尋 level:ERROR AND service:order-service
2. 找到 "Stock deduction failed at DB level"
3. 用 traceId 串聯 → 找到 MySQL 連線超時
4. Grafana → 確認 DB connection pool 已耗盡
```

---

## Spring Boot 設定（已加入三個服務）

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    tags:
      service: order-service
      env: k8s

# Tracing（K8s 環境透過環境變數注入）
# OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
# OTEL_SERVICE_NAME=order-service
# MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
```

---

## 依賴（已加入 pom.xml）

```xml
<!-- Metrics: Prometheus -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Tracing: OpenTelemetry + Jaeger -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry.exporter</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- Structured Logging for ELK -->
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>7.4</version>
</dependency>
```
