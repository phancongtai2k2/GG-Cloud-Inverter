# Metrics backend

`GET /metrics` (định dạng Prometheus) — chỉ trong mạng nội bộ: Caddy chỉ chuyển `/api/*`, `/ws/*`, `/healthz` ra ngoài.
Bước A3 của `backend/docs/KE_HOACH_NANG_CAP_BACKEND.docx`; code ở `src/GgPower.Infrastructure/GgMetrics.cs`.

## Của ứng dụng (meter `GgPower`)

| Tên Prometheus | Loại | Ý nghĩa | Cảnh báo gợi ý |
|---|---|---|---|
| `ggpower_ingest_received_total` | counter | Bản tin MQTT nhận từ thiết bị | Tốc độ giảm mạnh so với số thiết bị × 0,25/s |
| `ggpower_ingest_rejected_total` | counter | Bản tin bị loại (topic / tenant / serial / payload sai) | Tăng đột biến |
| `ggpower_ingest_dropped_total` | counter | Dòng dữ liệu thô bị bỏ vì hàng đợi ghi đầy | Bất kỳ giá trị tăng nào |
| `ggpower_ingest_queue_length` | gauge | Dòng đang chờ ghi | > 50 % `ggpower_ingest_queue_capacity` |
| `ggpower_ingest_queue_capacity` | gauge | Dung lượng hàng đợi trước khi bỏ dòng | — |
| `ggpower_writer_rows_total` | counter | Dòng dữ liệu thô đã ghi DB | — |
| `ggpower_events_queue_length` | gauge | Sự kiện thiết bị (status, cảnh báo, config/state, firmware) chờ ghi DB | > 1.000 kéo dài |
| `ggpower_events_dropped_total` | counter | Status / firmware bị bỏ vì hàng đợi sự kiện đầy | Bất kỳ giá trị tăng nào |
| `ggpower_events_failed_total` | counter | Sự kiện bỏ cuộc sau khi thử lại (DB lỗi lâu) | Bất kỳ giá trị tăng nào |
| `ggpower_writer_batch_duration_milliseconds` | histogram | Thời gian ghi một lô | p95 > 1.000 ms |
| `ggpower_rollup_duration_milliseconds` | histogram | Thời gian một lần job tổng hợp (chu kỳ 30 s) | p95 > 15.000 ms (nửa chu kỳ) |
| `ggpower_commands_submitted_total{group}` | counter | Lệnh ghi cấu hình được nhận | — |
| `ggpower_commands_finished_total{status}` | counter | Lệnh kết thúc: applied / failed / reverted… | Tỉ lệ failed tăng |
| `ggpower_realtime_connections` | gauge | Kết nối WebSocket đang mở | — |
| `ggpower_devices_known` | gauge | Thiết bị trong bộ nhớ | — |
| `ggpower_devices_reachable` | gauge | Thiết bị có dữ liệu trong 90 s gần nhất | Giảm đột ngột (mất broker / mạng) |

## Của ASP.NET Core

`http_server_request_duration_seconds` (histogram theo route, method, mã trả về), `http_server_active_requests`,
`kestrel_*`… theo chuẩn OpenTelemetry.

## Đi kèm

- `/healthz/ready` (503 khi DB / MQTT / hàng đợi có vấn đề) cho load balancer; `/healthz` vẫn trả bộ đếm dạng JSON.
- Dashboard Grafana và luật cảnh báo: bước C6.
