# Kênh realtime (WebSocket)

Phần hợp đồng mà OpenAPI không diễn đạt được. Viết lại từ code ngày 11/10/2026:
`backend/src/GgPower.Api/Endpoints/DataEndpoints.cs` (`RealtimeEndpoints`), `backend/src/GgPower.Infrastructure/LiveState.cs`
và `app/app/src/main/java/vn/ggpower/inverter/data/remote/Realtime.kt`. Đổi gì ở đây thì ghi vào `CHANGES.md`.

## Kết nối

1. `POST /api/v1/realtime/ticket` (Bearer) → `{"ticket": "...", "expires_in": 30}`. Vé dùng **một lần**, sống 30 s.
   Access token không bao giờ nằm trong URL.
2. Mở `wss://<host>/ws/v1/realtime?ticket=<ticket>`.
   - Không phải yêu cầu WebSocket → HTTP 400. Vé sai / hết hạn / đã dùng → HTTP 401 (app dừng thử lại, lấy vé mới sau khi refresh token).
3. Máy chủ gửi khung ping WebSocket mỗi 20 s. App tự kết nối lại khi đứt, chờ 1 s, 2 s, 4 s… tối đa 30 s, mỗi lần một vé mới.

Mọi frame là JSON văn bản UTF-8, tên trường snake_case, frame từ máy chủ luôn có `type`.

## App → máy chủ

| Frame | Ý nghĩa |
|---|---|
| `{"subscribe": ["<project id>", ...]}` | Thay toàn bộ danh sách theo dõi (tối đa 20 dự án; id không phải dự án của người dùng bị bỏ qua). Gửi lại mỗi lần kết nối. |
| `{"ping": 1}` | Máy chủ trả `{"type": "pong"}`. |

Frame không phải JSON object, frame nhị phân, hoặc lớn hơn 4 KB → máy chủ đóng kết nối.

## Máy chủ → app

| `type` | Nội dung | Khi nào |
|---|---|---|
| `subscribed` | `{"type":"subscribed","serials":["..."]}` | Ngay sau mỗi `subscribe`: các serial thực sự được theo dõi |
| `device` | Đúng schema `DeviceSnapshot` trong `openapi.yaml` (giống `GET /projects/{id}/telemetry`) | Ngay sau `subscribed` (mỗi thiết bị một frame), rồi mỗi khi datalogger gửi dữ liệu (≈ 5 s telemetry, ≈ 20 s pin) hoặc đổi trạng thái |
| `alerts_changed` | `{"type":"alerts_changed","serial":"..."}` | Có cảnh báo mới / kết thúc / đánh dấu xử lý. App gọi lại `GET /projects/{id}/alerts` hoặc `/overview` |
| `config_changed` | `{"type":"config_changed","serial":"..."}` | Datalogger báo cấu hình đổi (kể cả đổi ở màn LCD). App gọi lại `GET /devices/{serial}/config` |
| `pong` | `{"type":"pong"}` | Trả lời `ping` |

App phải bỏ qua `type` lạ và trường lạ, để máy chủ thêm loại frame mới mà không làm hỏng app cũ.

## Giới hạn và bảo đảm

- Mỗi kết nối có hàng đợi 32 frame; khi app đọc chậm, frame **cũ nhất** bị bỏ. `device` luôn là ảnh chụp đầy đủ nên mất frame không làm sai trạng thái, chỉ trễ.
- Không có thứ tự giữa các thiết bị, không có số thứ tự frame, không phát lại frame đã lỡ. Sau khi kết nối lại, bộ `device` gửi kèm `subscribed` là trạng thái đúng.
- `telemetry` / `battery` trong frame `device` là `null` khi thiết bị không liên lạc được; `status` khi đó là `offline`.

## Dự kiến khi backend chạy nhiều instance

Vé và danh sách theo dõi hiện nằm trong RAM của một tiến trình. Khi tách nhiều instance (giai đoạn nâng cấp
backend), vé sẽ chuyển sang Redis và frame phát qua pub/sub. **Giao thức phía app giữ nguyên**; nếu phải đổi thì
tăng phiên bản đường dẫn (`/ws/v2/...`) và ghi vào `CHANGES.md`.

## MQTT (datalogger ↔ backend)

Không thuộc hợp đồng app ↔ backend. Đặc tả topic và payload nằm ở kho firmware (`doc/MQTT_API.md`). Tóm tắt:
lên `solar/{tenant}/inverter/{serial}/up/{telemetry|battery|backfill|status|config/state|firmware|alarm|command/ack}`,
xuống `solar/{tenant}/inverter/{serial}/down/command`, JSON ≤ 16 KB.
