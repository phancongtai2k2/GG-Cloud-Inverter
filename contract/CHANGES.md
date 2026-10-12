# Nhật ký thay đổi hợp đồng app ↔ backend

Đây là file bàn giao giữa backend và app. **Mọi thay đổi** ở `openapi.yaml` hoặc `realtime.md` phải có một mục ở
đây, mục mới nhất ở trên cùng. Bên kia đọc file này để biết cần làm gì.

## Cách ghi một mục

```
## YYYY-MM-DD · <tiêu đề ngắn>
- Loại: thêm | đổi | bỏ | sửa tài liệu      Phá tương thích: có | không
- Phạm vi: METHOD /đường/dẫn  (hoặc frame realtime `type`)
- Chi tiết: trường nào, kiểu gì, null khi nào, mã lỗi mới…
- Backend: trạng thái (đã merge / đang làm), PR hoặc commit
- App cần làm: việc cụ thể, hoặc "không"
```

## Quy tắc tương thích

- **Không phá tương thích** (làm ngay, không cần bản app mới): thêm endpoint; thêm trường vào phản hồi; thêm giá trị
  enum mà app có nhánh `else`/`Unknown`; thêm `type` frame realtime; thêm tham số query tuỳ chọn.
- **Phá tương thích** (cần kế hoạch): đổi tên / bỏ / đổi kiểu trường; đổi nghĩa của `null`; thêm trường bắt buộc
  vào yêu cầu; đổi mã lỗi; đổi đường dẫn. Cách làm: thêm cái mới song song → app chuyển sang → bản app cũ hết dùng →
  mới bỏ cái cũ. Nếu không thể song song thì tăng phiên bản (`/api/v2`).
- App phải bỏ qua trường lạ và giá trị enum lạ (map về `Unknown`), không được lỗi.
- Backend chạy `ContractTests` (route trong `openapi.yaml` phải khớp route thật, mọi `$ref` phải trỏ đúng).

---

## 2026-10-12 · Trợ lý chat AI của dự án
- Loại: thêm      Phá tương thích: không
- Phạm vi: POST /projects/{projectId}/chat/messages; GET /projects/{projectId}/chat/conversations;
  GET, DELETE /projects/{projectId}/chat/conversations/{conversationId}
- Chi tiết:
  - Hỏi: `{text, conversation_id?}` → `ChatAnswer {conversation_id, answer, refused}`. Không có `conversation_id` thì tạo
    hội thoại mới. Trả lời mất vài giây đến ~30 s (trợ lý tra dữ liệu thiết bị, thống kê, cảnh báo, thời tiết rồi mới trả lời):
    timeout đọc của app ≥ 60 s. `answer` là Markdown đơn giản. `refused = true`: trợ lý từ chối vì lý do an toàn, không lưu gì.
  - Mỗi hội thoại thuộc một người dùng và một dự án; người khác (kể cả thành viên khác của dự án) không đọc được.
  - Mã lỗi mới: 503 `CHAT_DISABLED` (máy chủ chưa có API key: app ẩn hoặc làm mờ mục chat), 404 `CONVERSATION_NOT_FOUND`,
    409 `CONVERSATION_FULL` (kèm `max_questions`, mặc định 30 câu: app mở hội thoại mới), 409 `CONVERSATION_BUSY`,
    429 `CHAT_QUOTA_EXCEEDED` (kèm `max_questions_per_day`, mặc định 50 câu / 24 giờ), 502 `CHAT_UNAVAILABLE`.
- Backend: `Api/Chat/` (Claude qua SDK chính thức của Anthropic, 6 công cụ chỉ đọc dữ liệu của dự án), migration 007;
  test `ChatTests`. Bật bằng `ANTHROPIC_API_KEY` (`backend/docs/DEPLOY.md`).
- App cần làm: màn chat (danh sách hội thoại, khung hỏi đáp, trạng thái "đang trả lời", xử lý các mã lỗi trên).

## 2026-10-11 · Metrics Prometheus
- Loại: thêm      Phá tương thích: không
- Phạm vi: GET /metrics (ngoài /api/v1, nội bộ — Caddy không chuyển ra ngoài)
- Chi tiết: định dạng Prometheus; danh sách và ý nghĩa ở `backend/docs/METRICS.md`.
- Backend: bước A3 (OpenTelemetry + exporter Prometheus).
- App cần làm: không.

## 2026-10-11 · Health check liveness / readiness
- Loại: thêm      Phá tương thích: không
- Phạm vi: GET /healthz/live, GET /healthz/ready (ngoài /api/v1, dành cho load balancer / vận hành)
- Chi tiết: `live` luôn 200; `ready` 200 khi DB trả lời ≤ 2 s, MQTT đã nối (nếu instance nhận dữ liệu) và hàng đợi ghi
  < 80 %, ngược lại 503 `{status: not_ready, problems: [database | mqtt | ingest_queue]}`. `/healthz` giữ nguyên.
- Backend: bước A2 của `backend/docs/KE_HOACH_NANG_CAP_BACKEND.docx`; test `ScaleTests`.
- App cần làm: không.

## 2026-10-11 · SOC dự phòng bị chặn khi không có truyền thông BMS
- Loại: thêm      Phá tương thích: không
- Phạm vi: GET /devices/{serial}/config (`write_access.blocked_groups[].reason`); POST /devices/{serial}/config-commands
- Chi tiết: khi inverter báo không có pin, hoặc `bms_comm` là `fault` / `disabled`, nhóm `backup_soc` bị bỏ khỏi
  `writable_groups` và có trong `blocked_groups` với `reason = BMS_NOT_CONNECTED` (giá trị enum mới; `unknown` hay chưa báo thì
  không chặn). Gửi lệnh `backup_soc` lúc đó trả `409 BMS_NOT_CONNECTED` (mã lỗi mới). Có lại truyền thông là mở lại ngay.
- Backend: `DeviceLive.BmsDisconnected`; test `BackupSocIsWrittenAndReadBack`.
- App cần làm: đã làm — đọc `blocked_groups` → `BlockReason.BmsNotConnected` (màn 16 hiện "Chưa có truyền thông với pin (BMS)…",
  thanh SOC mờ); mã lỗi `BMS_NOT_CONNECTED` đã có sẵn ở luồng ghi.

## 2026-10-11 · Thẻ Datalogger; giới hạn SOC sạc / xả của pin
- Loại: thêm      Phá tương thích: không
- Phạm vi: GET /projects/{projectId}/devices (`DeviceEntry.datalogger`); GET /projects/{projectId}/telemetry và frame `device`
  (`DeviceSnapshot.datalogger`, `BatteryLive.charge_limit_soc_percent`, `BatteryLive.discharge_limit_soc_percent`); MQTT `up/status`
- Chi tiết:
  - `Datalogger {serial, online, connection, rssi_dbm, ip, firmware}`: lấy từ `up/status` gần nhất (`signal_rssi`, `ip_addr`,
    `logger_fw`); giữ qua khởi động lại backend (đọc `devices.status_json`). App quy `rssi_dbm` ra mức tín hiệu.
  - **Yêu cầu firmware datalogger** (quyết định 11/10/2026): gửi thêm `logger_sn` (serial riêng của datalogger) và `conn_type`
    (`wifi` | `ethernet` | `4g`) trong `up/status`. Chưa gửi thì `serial = null` (app hiện "—"), `connection = "wifi"`.
  - `discharge_limit_soc_percent` = SOC dự phòng (`battery_reserve_soc` của `config/state`).
    `charge_limit_soc_percent` đọc `charge_stop_soc` trong `config/state` — **yêu cầu firmware** gửi trường này; chưa có thì null
    (app ẩn vạch sạc).
- Backend: đã làm trên nhánh `feature/contract-and-theme`; test `DataloggerLinkAndBatteryLimitsReachTheDeviceScreens`.
- App cần làm: thẻ Datalogger (màn Thiết bị) và vạch giới hạn (màn Năng lượng pin) đọc các trường này ở bản live.

## 2026-10-11 · Màn chính: CO₂ hôm nay, sạc pin hôm nay theo nguồn, model
- Loại: thêm      Phá tương thích: không
- Phạm vi: GET /projects/{projectId}/overview; GET /projects/{projectId}/telemetry và frame realtime `device` (`DeviceSnapshot`)
- Chi tiết:
  - `Overview.model` (string | null): model inverter.
  - `Overview.co2_today_kg`, `DeviceSnapshot.co2_today_kg` (number | null): `yield_today_kwh × Stats:Co2KgPerKwh`, cùng hệ số với
    `/stats` `summary.co2_kg`. Overview null khi chưa gắn thiết bị; frame null khi thiết bị không liên lạc được.
  - `Overview.battery_charged_today`, `DeviceSnapshot.battery_charged_today` (`BatteryChargedToday` | null):
    `{from_pv_kwh, from_grid_kwh}` = kWh đã sạc vào pin từ 00:00 giờ địa phương của dự án; `from_pv_kwh` = `rollup_1d.pv_charge_kwh`,
    `from_grid_kwh` = `charge_kwh − pv_charge_kwh` (không âm). Null khi inverter báo không có pin. Frame lấy số từ bộ nhớ, được
    job tổng hợp làm mới mỗi 30 s (trễ ≈ 30 s so với telemetry).
- Backend: đã làm trên nhánh `feature/contract-and-theme` (`LiveState.cs`, `Ingest.cs` `RollupService.RefreshChargedTodayAsync`,
  `DataEndpoints.cs`); test `SamplesAreStoredAndRolledUpIntoStatistics`.
- App cần làm: đọc `co2_today_kg` → `HomeOverview.co2TodayKg`, `battery_charged_today` → `HomeOverview.chargedToday` ở cả
  `/overview` và frame `device` (thay "—" ở bản live).

## 2026-10-11 · Khởi tạo hợp đồng chung
- Loại: sửa tài liệu      Phá tương thích: không
- Phạm vi: toàn bộ API v1 (55 thao tác) và kênh realtime
- Chi tiết: `openapi.yaml` và `realtime.md` viết lại từ code backend commit f9308aa; không đổi hành vi nào.
  Thay cho `app/docs/api/config-write.openapi.yaml` (nháp 0.2.0, đã lệch: `blocked_groups[].reason` thực tế là
  `NOT_SUPPORTED_BY_DEVICE`, có thêm `outcome.unsupported`, `failure.device_code`, `DeviceSettings` chỉ còn 5 trường).
- Backend: thêm `tests/GgPower.Tests/ContractTests.cs`.
- App cần làm: dùng `contract/` thay cho `app/docs/api/`. Bước tiếp theo đề xuất: chuyển parse JSON thủ công trong
  `data/remote/Live*Repositories.kt` sang DTO `@Serializable` theo schema ở đây, kèm test parse.

## Đang chờ (thiết kế mới cần backend bổ sung)

Các trường dưới đây **chưa có** trong hợp đồng. Khi backend làm xong mục nào thì chuyển thành một mục có ngày ở trên.

| Màn (thiết kế mới) | Dữ liệu cần | Đề xuất vị trí | Ghi chú |
|---|---|---|---|
| Device · Datalogger; BatteryEnergy · vạch sạc | **Firmware** gửi `logger_sn`, `conn_type` (up/status) và `charge_stop_soc` (config/state) | Backend đã đọc sẵn (mục 2026-10-11 ở trên) | Chưa gửi thì app hiện "—" / ẩn vạch sạc; không cần đổi hợp đồng khi firmware làm xong |
