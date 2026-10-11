# Ghi chú triển khai backend

Những thay đổi cấu hình máy chủ đi kèm các bước nâng cấp (`backend/docs/KE_HOACH_NANG_CAP_BACKEND.docx`). Claude không
tự áp dụng lên máy chủ thật; người vận hành làm theo mục này khi deploy bản có bước tương ứng.

## A5 · MQTT persistent session (không mất dữ liệu khi backend tắt)

Backend giữ phiên trên broker (`Mqtt:PersistentSession = true`, mặc định). Khi backend dừng (deploy, sự cố) broker
xếp hàng bản tin của thiết bị và gửi lại khi backend nối lại.

1. **Broker** — `deploy/mqtt/config/mosquitto.conf` đã có:
   ```
   max_queued_messages 800000        # ≈ 10 phút ở 5.000 thiết bị (quyết định D2)
   queue_qos0_messages true          # datalogger có thể gửi QoS 0
   persistent_client_expiration 1d   # bỏ phiên của client vắng quá 1 ngày
   ```
   Khởi động lại container `mqtt` sau khi đổi. RAM broker khi hàng đầy: ước 400–600 MB thêm (bản tin ~0,5 KB).
   Tăng / giảm `max_queued_messages` theo số thiết bị: thiết bị × 0,25 × số giây muốn giữ.
2. **Client id cố định** — broker nhận lại phiên theo client id. Mosquitto production bật
   `use_username_as_clientid`, nên client id chính là tên đăng nhập MQTT của backend: **không** dùng chung tài khoản đó
   cho công cụ khác (sẽ chiếm phiên của backend). Broker khác: đặt `GGP_Mqtt__ClientId` (ví dụ `ggpower-backend-1`).
3. **Lần deploy đầu tiên** sau bước này: phiên cũ là "clean session", nên bản tin trong lúc đổi phiên bản vẫn có thể
   mất; từ lần deploy sau thì không.
4. **Kiểm tra** sau deploy: `ggpower_ingest_received_total` tăng vọt ngay khi nối lại (broker xả hàng), không có
   `ggpower_ingest_dropped_total` tăng; số mẫu trong khoảng tắt máy đầy đủ.

Tắt (quay về hành vi cũ): `GGP_Mqtt__PersistentSession=false`.

Đo thực tế (docs/LOADTEST.md mục A5): tắt backend 61 s ở 1.000 thiết bị → không thiếu mẫu nào trong khoảng tắt; giết
tiến trình đột ngột mất ≤ 1 s dữ liệu đang chờ ghi, dừng bình thường thì không mất (cần `stop_grace_period: 30s`, đã có
trong `docker-compose.yml`).

## Job định kỳ khi chạy nhiều instance (bước B1)

Mỗi job chỉ chạy ở một instance: instance giữ advisory lock của Postgres (`pg_try_advisory_lock(18247, n)`) trên một
kết nối riêng. Instance đó dừng / chết / mất kết nối DB thì Postgres nhả khoá, instance khác nhận ở lần thử kế tiếp
(≤ 1 chu kỳ của job: rollup 30 s, cảnh báo offline 10 s, lệnh 20 s, báo cáo ngày 1 phút).

| Job | Việc |
|---|---|
| `rollup` | Tổng hợp 15 phút / ngày, partition, dọn audit / OTP / session / `daily_reports_sent` |
| `daily-report` | Báo cáo 20:00; ghi `daily_reports_sent` nên không gửi 2 lần khi đổi instance |
| `offline-alerts` | Mở / đóng cảnh báo mất kết nối (đẩy trạng thái online cho app thì instance nào cũng làm) |
| `commands` | Nạp lại TOU lúc sang ngày; đóng lệnh không cập nhật quá 5 phút (instance chạy lệnh đã dừng) |

Lưu ý:
- `rollup` và `offline-alerts` dựa vào dữ liệu nhận được ở chính instance đó (danh sách thiết bị có dữ liệu mới, trạng
  thái live) → leader của chúng phải là instance **nhận dữ liệu MQTT**. Cấu hình vai trò ở B7 bảo đảm điều này; khi
  nhận job, instance chạy một lần quét đầy đủ trước.
- Xem ai đang giữ: metric `ggpower_jobs_leader{job}`, hoặc SQL
  `SELECT objid, pid, application_name, client_addr FROM pg_locks JOIN pg_stat_activity USING (pid) WHERE locktype = 'advisory' AND classid = 18247;`
- Lệnh đang chạy khi instance bị dừng: trước đây đóng ngay lúc khởi động lại, nay đóng sau 5 phút (`ACK_TIMEOUT`).
