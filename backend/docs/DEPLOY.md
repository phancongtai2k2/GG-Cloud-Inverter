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
