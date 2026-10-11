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
| Device · thẻ Datalogger Wi-Fi | serial datalogger, online, cường độ tín hiệu (RSSI → mức), kiểu kết nối, firmware | `DeviceEntry.datalogger` và `DeviceSnapshot.datalogger` | Datalogger đã gửi `rssi`, `ip`, `logger_fw` trong `up/status` (backend có `Rssi` ở `Telemetry.cs`) nhưng chưa đưa ra API |
| BatteryEnergy · vạch giới hạn | giới hạn SOC sạc / xả (%) từ cấu hình BMS | `DeviceSnapshot.battery.charge_limit_soc_percent`, `discharge_limit_soc_percent` | Cần firmware gửi nếu chưa có |
| Config · thông báo "chưa có truyền thông BMS" | cờ truyền thông BMS | đã có `bms_comm` ở `DeviceEntry` / `DeviceSnapshot` | Có thể không cần trường mới; cân nhắc thêm vào `ConfigSnapshot` cho tiện |
| Main · chip CO₂ | CO₂ giảm hôm nay (kg) | `Overview.co2_today_kg` | Hiện chỉ có ở `/stats` (summary.co2_kg) |
| Main · tên thiết bị trên sơ đồ nhà | model inverter | `Overview.model` | Đã có ở `DeviceSnapshot.model` |
| Main · "Dung lượng sạc hôm nay (Từ PV / Từ lưới)" | kWh **đã sạc vào pin hôm nay**, tách theo nguồn PV / lưới | `Overview.battery_charged_today: {from_pv_kwh, from_grid_kwh}` (và trong frame `device`) | Chủ sản phẩm chốt 11/10/2026. Khác `battery_content` (năng lượng đang trong pin). Tính từ `rollup_15m.pv_charge_kwh` và `charge_kwh − pv_charge_kwh` của ngày địa phương |
