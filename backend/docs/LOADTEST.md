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

## A7 — chốt giai đoạn A (12/10/2026)

Bản có A1–A6 và sửa lỗi ghi trạng thái thiết bị (dưới), cùng máy, mỗi mức 6 phút.

| Thiết bị | nhận/s (kỳ vọng) | bỏ | ghi lô | tổng hợp TB / max | CPU API | CPU PG TB / max | RAM API |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1.000 | 253 (250) | 0 | 106 ms | 0,7 s / 0,8 s | 4 % | 6 % / 47 % | 171 MB |
| 2.000 | 506 (500) | 0 | 117 ms | 0,7 s / 1,0 s | 7 % | 3 % / 7 % | 183 MB |
| 5.000 | 1.264 (1.250) | 0 | 156 ms | 3,5 s / 4,1 s | 16 % | 18 % / 107 % | 195 MB |
| **10.000** | 2.528 (2.500) | **0** | 265 ms | 7,0 s / 11,8 s | 25 % | 47 % / 127 % | 204 MB |

So với nền: 5.000 thiết bị tổng hợp 11,6 s → 3,5 s, CPU Postgres 49 % → 18 %.

**Lỗi tìm thấy khi chạy 10.000 thiết bị** (trước khi sửa: bỏ 366.612 dòng dữ liệu thô trong 6 phút): `BatchWriter`
ghi trạng thái thiết bị (online, last_seen, năng lượng trong pin) mỗi 15 s bằng Dapper với danh sách tham số — chạy
**một UPDATE cho mỗi thiết bị**, 11.000 lượt mỗi 15 s, giữ luồng ghi dữ liệu thô ~11 s → hàng đợi 20.000 dòng tràn.
Đã có từ trước nâng cấp (ở 5.000 thiết bị mất ~5 s / 15 s, hàng đợi còn đỡ được). Sửa: một câu `UPDATE … FROM unnest(…)`.

### Năng lực một máy sau giai đoạn A (đo, máy dev 12 luồng)

- **10.000 thiết bị** chạy ổn: không mất dữ liệu, job tổng hợp dùng ~¼ chu kỳ, Postgres ~½ lõi trung bình.
- Trần kế tiếp ước ~20.000 thiết bị: job tổng hợp (tăng tuyến tính, ~0,7 ms/thiết bị/lần) chạm 15 s, luồng ghi dữ liệu
  thô ~5.000 dòng/s. Muốn hơn nữa: giai đoạn C (nhiều worker, TimescaleDB).
- Dung lượng không đổi: ≈ 3,2 MB dữ liệu thô / thiết bị / ngày → 10.000 thiết bị giữ 90 ngày ≈ 2,8 TB — cần C4
  (nén) hoặc giảm thời gian giữ trước khi tới quy mô đó.

## B7 — hai instance sau Caddy (12/10/2026)

`tools/loadtest/docker-compose.multi.yml` (image production, Caddyfile production) + `run-multi.ps1`: instance 1
`api,ingest,jobs`, instance 2 `api`, Redis + SeaweedFS; 5.000 thiết bị trong 6 phút; một "app" đọc `/overview` qua Caddy
mỗi 200 ms; **instance 2 bị dừng ở giây 128 và chạy lại ở giây 191**. Mọi thứ chạy trong Docker Desktop (Windows), nên
số tuyệt đối không so được với các mục trên (đo tiến trình chạy thẳng trên máy); so trong cùng điều kiện ở bảng dưới.

| Cấu hình (trong container) | nhận/s | bỏ | tổng hợp TB | CPU inst. 1 | CPU inst. 2 | CPU PG | CPU Redis | app gọi / lỗi | app p50 / p99 / max |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 instance, memory (nền) | 1.264 | 0 | 4,6 s | 46 % | — | 29 % | 1 % | 1.770 / 0 | 15 / 51 / 214 ms |
| 2 instance, redis, lần 1 | 1.264 | 0 | 6,9 s | 124 % | 42 % | 46 % | 11 % | 1.701 / **0** | 19 / 82 / 3.274 ms |
| 2 instance, redis, sau sửa | 1.264 | 0 | 8–11 s ¹ | 59 % | 14 % | 48 % | 4 % | 1.719 / **0** | 17 / 99 / 3.293 ms |

CPU tính theo % một lõi. ¹ Bỏ lần quét đầy đủ lúc khởi động (72 s, chồng lên khoá bảng của script seed).

- **Dừng instance 2 giữa chừng: app không lỗi lần nào** (414 lần gọi trong khoảng dừng → chạy lại + 30 s); request đang
  dở lúc dừng được Caddy thử lại ở instance 1, chậm nhất ~3,3 s (thời gian dừng có trật tự + `lb_try_duration`).
- **Lỗi tìm thấy và đã sửa**: lần 1, instance nhận dữ liệu dựng và publish frame realtime cho **mọi** bản tin
  (1.250/s) vì không biết instance khác có ai xem → +0,8 lõi. Sau sửa, mỗi instance chia sẻ danh sách thiết bị đang được
  xem mỗi giây (`rtwatch`), frame chỉ dựng cho thiết bị đó: chi phí chế độ redis còn ~+0,13 lõi so với memory.
- Ghi live state lên Redis (C2) ở 5.000 thiết bị: Redis ~4 % một lõi.
- Chưa rõ: job tổng hợp chậm hơn ở chế độ 2 instance (8–11 s so với 4,6 s), dù không phần nào của Redis chạm vào job này;
  giữa các lần chạy trong Docker Desktop dao động lớn (6,9 s ở lần 1). Vẫn dưới chu kỳ 30 s; **cần đo lại trên máy chủ
  thật** trước khi dựa vào con số này cho quy mô > 5.000 thiết bị.
