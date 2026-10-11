# Thử tải backend

Bước A0 của `backend/docs/KE_HOACH_NANG_CAP_BACKEND.docx`. Mỗi giai đoạn nâng cấp chạy lại đúng bộ này và thêm một bảng
kết quả mới bên dưới để so sánh.

## Cách chạy

```powershell
# 1. Broker thử (không xác thực) và database riêng
docker run -d --name ggpower-mqtt-load -p 1884:1883 -v <repo>\backend\tools\loadtest\mosquitto.conf:/mosquitto/config/mosquitto.conf eclipse-mosquitto:2
docker exec ggpower-pg-test psql -U ggpower -d ggpower_test -c "CREATE DATABASE ggpower_load;"
# 2. Backend bản Release (cổng 5090, log thời gian job tổng hợp ở mức Debug)
dotnet build backend\src\GgPower.Api -c Release
backend\tools\loadtest\run-backend.ps1          # chạy nền, giữ cửa sổ / tác vụ này
# 3. Mỗi mức tải: nạp lịch sử 4 ngày + 35 phút dữ liệu thô, chạy LoadGen, đo, in tóm tắt (thêm vào results.jsonl)
backend\tools\loadtest\run.ps1 -Devices 1000 -Minutes 6
```

- `tools/GgPower.LoadGen`: N datalogger giả gửi `up/status` một lần, `up/telemetry` mỗi 5 s, `up/battery` mỗi 20 s,
  lệch pha ngẫu nhiên, QoS 1, đường công suất theo giờ trong ngày.
- `tools/loadtest/seed.sql`: lịch sử để job tổng hợp làm việc trên lượng dữ liệu như chạy thật lâu ngày (4 ngày
  `rollup_15m`, 35 phút `telemetry_raw`). Không nạp thì job chạy rất nhanh và số đo sai.
- `tools/load_test.py` (cũ) vẫn giữ: đo độ trễ đầu-cuối trên máy chủ thật, cần Python và secrets.

## Cột kết quả

| Cột | Nghĩa |
|---|---|
| nhận/s | Bản tin backend nhận mỗi giây (`/healthz` `messages_received`) so với kỳ vọng N × 0,25 |
| bỏ | Dòng bị bỏ do hàng đợi ghi đầy (`rows_dropped`) |
| ghi lô | Thời gian ghi một lô gần nhất (ms) |
| tổng hợp TB / max | Thời gian một lần `RollupService.RunOnceAsync` (job chạy 30 s một lần, tính từ lúc lần trước xong) |
| CPU API | % của **một** lõi, trung bình |
| CPU PG TB / max | % của một lõi (docker stats; > 100 % là nhiều lõi) |
| MB/thiết bị/ngày | Ước dung lượng `telemetry_raw`: dữ liệu dòng + 28 B header + ~40 B khoá chính, × 17.280 dòng/ngày; chưa gồm `battery_raw` (~¼ số dòng) |

## Kết quả nền (trước nâng cấp) — 11/10/2026

Máy: laptop 12 luồng CPU, 14 GB RAM; Docker 12 CPU / 6,6 GB; Postgres 17 và Mosquitto 2 trong Docker, backend chạy
trực tiếp (Release, .NET 10 roll-forward). Mỗi mức chạy 6 phút.

| Thiết bị | nhận/s (kỳ vọng) | bỏ | ghi lô | tổng hợp TB / max | CPU API | CPU PG TB / max | RAM API |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1.000 | 253 (250) | 0 | 128 ms | 2,9 s / 3,4 s | 4 % | 7 % / 25 % | 131 MB |
| 2.000 | 505 (500) | 0 | 110 ms | 5,9 s / 6,9 s | 7 % | 13 % / 102 % | 155 MB |
| 5.000 | 1.264 (1.250) | 0 | 235 ms | 11,6 s / 14,8 s | 13 % | 49 % / 149 % | 184 MB |

Dung lượng `telemetry_raw`: ≈ 192 B/dòng → **≈ 3,2 MB/thiết bị/ngày** (90 ngày ≈ 285 MB/thiết bị, ≈ 285 GB cho 1.000
thiết bị), chưa tính `battery_raw` và các trường firmware thật gửi thêm.

### Nhận xét

- **Nhận và ghi dữ liệu còn rất dư**: 5.000 thiết bị chỉ dùng ~13 % một lõi, không bỏ bản tin, lô ghi < 250 ms.
- **Job tổng hợp là điểm nghẽn**, tăng gần tuyến tính ~2,3–2,9 ms/thiết bị/lần chạy. Khoảng **10.000–12.000 thiết bị**
  thì một lần chạy dài bằng cả chu kỳ 30 s: database bận liên tục, số liệu thống kê trễ dần. Trong lúc chạy Postgres
  chiếm 1–1,5 lõi.
- Dung lượng dữ liệu thô là trần thứ hai: vài trăm GB cho mỗi nghìn thiết bị nếu giữ 90 ngày.
- Chưa đo: WebSocket / REST dưới tải người dùng, mất kết nối broker, mẫu đến trễ. Bổ sung khi tới các bước tương ứng.

## Sau A4 — tổng hợp tăng dần (11/10/2026)

Cùng máy, cùng cách chạy, 5.000 thiết bị, 6 phút.

| Thiết bị | nhận/s (kỳ vọng) | bỏ | ghi lô | tổng hợp TB / max | CPU API | CPU PG TB / max | RAM API |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 5.000 (nền) | 1.264 (1.250) | 0 | 235 ms | 11,6 s / 14,8 s | 13 % | 49 % / 149 % | 184 MB |
| **5.000 (A4)** | 1.264 (1.250) | 0 | 178 ms | **2,4 s / 3,5 s** | 18 % | **27 % / 68 %** | 205 MB |

Từng thay đổi và tác dụng (đo theo thứ tự, mỗi lần một thay đổi):

| Thay đổi | Tổng hợp TB | Ghi chú |
|---|---:|---|
| Nền | 11,6 s | Quét 35 phút dữ liệu thô + dựng lại ~4 ngày `rollup_1d` của mọi thiết bị |
| Chỉ thiết bị có mẫu mới, ngày bị chạm | 7,8 s | Ở trạng thái ổn định mọi thiết bị đều có mẫu mới nên lợi ít |
| Nhìn lùi 1 phút thay vì 15 phút, `LATERAL` cho bảng ngày | 6,2 s | Bước 15 phút không nhanh lên dù đọc ít hơn 15 lần → nghẽn ở ghi |
| `fillfactor = 70` (cập nhật HOT) | 4,9 s | Chỉ 17 % lần cập nhật `rollup_15m` là HOT trước đó |
| Dòng ngày cập nhật **theo hiệu số** (mới − cũ của các khung đổi) | **2,4 s** | Không còn đọc lại mọi khung của ngày (mỗi khung một trang, tăng dần cả ngày) |

Trần ước theo job tổng hợp: ~2,4 s mỗi 30 s ở 5.000 thiết bị → còn dư nhiều lần; trần tiếp theo sẽ là tốc độ ghi
dữ liệu thô và dung lượng (giai đoạn C).

## A5 — tắt backend giữa chừng (11/10/2026)

1.000 thiết bị, 5 phút; phút thứ 1 **giết** tiến trình backend (`Stop-Process -Force`, như sự cố), 61 s sau bật lại.
Broker: persistent session của backend, `max_queued_messages 800000`, `queue_qos0_messages true`.

| | Kết quả |
|---|---|
| Mẫu trong 61 s backend tắt | **12.223** (≈ 12,2 / thiết bị — đủ) — broker giữ và giao lại khi nối lại |
| Tổng mẫu / kỳ vọng | 59.817 / ~60.000 (99,7 %) |
| Mất | 164 mẫu, **đúng lúc giết tiến trình**: đã nhận và báo nhận với broker nhưng còn trong hàng đợi ghi (≤ 1 s) |

Dừng **bình thường** (SIGTERM, `docker stop`) không mất mẫu nào: `BatchWriter` ghi nốt hàng đợi khi dừng (test
`ShutdownWritesQueuedSamples`); `docker-compose.yml` cho API `stop_grace_period: 30s`. Sự cố đột ngột vẫn có thể mất
tối đa khoảng 1 s dữ liệu đang chờ ghi — chấp nhận được; muốn bằng 0 phải báo nhận sau khi ghi DB, làm chậm cả luồng QoS 1.
