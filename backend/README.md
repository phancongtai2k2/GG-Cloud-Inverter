# GG Power backend

Nhận dữ liệu datalogger qua MQTT, lưu lịch sử, và cấp dữ liệu cho app Android qua REST + WebSocket.
.NET 9 (ASP.NET Core) + PostgreSQL 17. Định dạng bản tin thiết bị: `../doc/MQTT_API.md`.

```
Datalogger ─MQTT/TLS─ broker ─► MqttLink ─► IngestPipeline ─► BatchWriter ─► PostgreSQL
                                               │  LiveStateCache (RAM)        RollupService (15 phút, ngày)
                                               └► RealtimeHub ─WebSocket─► app
                          app ─REST /api/v1─► endpoint ─► CommandService ─MQTT─► datalogger
```

## Chạy trên máy phát triển (Windows)

Công cụ nằm ở `%LOCALAPPDATA%\ggpower-dev` (không cần quyền admin): `dotnet\` (.NET SDK 9), `pgsql\` + `pgdata\`
(PostgreSQL, cổng 5433, chỉ nghe localhost), `secrets.env` (mật khẩu DB, khoá JWT, tài khoản MQTT; mẫu ở `.env.example`).

```powershell
.\run.ps1                # chạy ở cổng 5080 (tự bật PostgreSQL nếu chưa chạy)
.\run.ps1 -Background    # chạy nền, log ở backend.log
.\run.ps1 -Stop
```

Kiểm tra: `http://localhost:5080/healthz`. Máy ảo Android gọi máy dev qua `http://10.0.2.2:5080`.
Ở chế độ phát triển, mã OTP được ghi ra `backend.log` (dòng `DEV OTP for ...`) vì chưa có nhà cung cấp SMS.

## Kiểm thử

**Test tự động** (`.
un.ps1 -Test`, khoảng 15 giây): 77 test trong `tests/GgPower.Tests`.
- `DomainTests.cs`: quy tắc thuần — khung giờ TOU (kể cả qua nửa đêm), ánh xạ chế độ ↔ lệnh datalogger, phân tích bản tin,
  quy tắc tài khoản và mật khẩu, ước tính nguồn năng lượng trong pin.
- `ApiTests.cs`: chạy cả ứng dụng với cơ sở dữ liệu `ggpower_test` (bị xoá trắng mỗi lần chạy; test từ chối chạy nếu tên
  DB không kết thúc bằng `_test`) và một thiết bị giả trả lời lệnh — tài khoản, phân quyền chéo, nhận dữ liệu và tổng hợp,
  cảnh báo, lệnh cấu hình (áp dụng, bị từ chối, không trả lời, xác nhận nhưng không ghi), phiếu hỗ trợ, WebSocket.

**Kiểm thử với hệ thống đang chạy:**

Cần backend đang chạy (`pip install requests websockets paho-mqtt`):

| Script | Phạm vi | Chạm thiết bị thật? |
|---|---|---|
| `tools/smoke_test.py` | Tài khoản, token, dự án, số liệu, phân quyền chéo tài khoản, realtime. Đặt `SMOKE_SERIAL=<serial chưa thuộc dự án nào>` để chạy phần dự án | Chỉ đọc |
| `tools/command_test.py` | Lệnh cấu hình, lịch TOU, công tắc khẩn cấp, cảnh báo, phiếu hỗ trợ | **Không.** Chỉ chạy với bộ giả lập `SIM-0001` và `SIM-LOCK --locked` (tenant `dev`) |
| `tools/load_test.py 200 180` | 200 thiết bị giả × 5 giây | Không |

Bộ giả lập: `..\.integration_repo\simulator\device_simulator.py`. Tenant `dev` chỉ được nhận khi `MQTT_TENANTS` có `dev`.

## Việc quản trị

- **Cho phép ghi cấu hình xuống một thiết bị** (mặc định mọi thiết bị chỉ xem): tài khoản có cờ `write_admin` gọi
  `PUT /api/v1/admin/config-write-allowlist/{serial}`. Cấp cờ bằng SQL:
  `UPDATE users SET role = 'ggp_technician', write_admin = true WHERE email = '...'` rồi đăng nhập lại.
- **Tắt ghi toàn hệ thống**: `PUT /api/v1/admin/config-write-switch {"enabled": false}`.
- **Phiếu hỗ trợ** (tài khoản có vai trò `ggp_technician`): `GET /api/v1/admin/tickets?status=received`,
  `POST /api/v1/admin/tickets/{id}/messages {"text": ...}`, `PUT /api/v1/admin/tickets/{id}/status {"status": "resolved" | "closed"}`.
- **Xoá dự án, trả thiết bị về trạng thái tự do**: chủ dự án gọi `DELETE /api/v1/projects/{id}` (app chưa có nút này).
  Số liệu đã lưu vẫn gắn với serial thiết bị.
- **Nội dung mục Khám phá**: đặt file JSON ở `src/GgPower.Api/Content/explore.json` (chưa có thì trả danh sách rỗng).

## Cấu trúc

- `src/GgPower.Domain` — quy tắc thuần: phân tích bản tin, lịch TOU, ánh xạ chế độ vận hành ↔ lệnh datalogger.
- `src/GgPower.Infrastructure` — DB (`Sql/*.sql` chạy tự động khi khởi động), bảo mật, ingest, tổng hợp, lệnh, thông báo.
- `src/GgPower.Api` — endpoint (`Endpoints/`), WebSocket, cấu hình bảo mật (`Program.cs`).
- `docs/SECURITY.md` — mô hình bảo mật và việc còn phải làm trước khi đưa ra Internet.

## Giới hạn hiện tại

- Chọn một chế độ khác TOU sẽ tắt "sạc theo lịch" trên biến tần (đúng đặc tả: lịch chỉ chạy ở TOU) và việc quay lại chế độ
  cũ không bật lại nó. Thay đổi này được ghi trong `changes` của lệnh (`charge_schedule_enabled`), nhưng hộp xác nhận của
  app hiện chỉ nêu chế độ.
- Firmware datalogger chưa có lệnh ghi SOC dự phòng: API báo `backup_soc` là nhóm bị chặn.
- Khung xả cần datalogger firmware 1.0.17 trở lên (nhận biết qua trường `discharge_schedule` trong bản tin cấu hình).
  Thiết bị cũ hơn: khung xả chỉ được lưu ở backend và lệnh trả `outcome.unsupported: ["discharge_slots"]`; app hiện cảnh báo.
- Một dự án hiển thị số liệu của thiết bị đầu tiên; chưa cộng gộp nhiều biến tần.
- SMS/e-mail OTP và thông báo đẩy mới là giao diện cắm (`IOtpSender`, `IPushSender`), bản mặc định chỉ ghi log.
- Tỷ lệ năng lượng trong pin theo nguồn dùng quy tắc "tỷ lệ" (đặc tả còn để ngỏ FIFO hay tỷ lệ); khi chưa có lịch
  sử, phần đang chứa trong pin được coi là từ PV.
- Giá điện bậc thang EVN chưa có bảng giá: tiền tiết kiệm dùng `Stats:DefaultPricePerKwh`.
- Datalogger không lưu đệm: thời gian broker hoặc mạng gián đoạn là khoảng trống trong số liệu.
- Khi cơ sở dữ liệu ngừng lâu, các mẫu cũ nhất trong hàng đợi bị bỏ (đếm ở `rows_dropped` của `/healthz`).

## Máy chủ (VPS)

Toàn bộ nằm trong `/opt/ggpower` trên máy chủ, chạy bằng Docker Compose (tệp nguồn ở `deploy/`):

| Thành phần | Vai trò | Ra Internet |
|---|---|---|
| `web` (Caddy) | HTTPS tự cấp và gia hạn chứng chỉ, trang web tĩnh trong `www/`, chuyển `/api`, `/ws`, `/healthz` tới API | 80, 443 |
| `mqtt` (Mosquitto) | Broker MQTT cho datalogger; dùng chung chứng chỉ TLS với web | 8883 (TLS) |
| `api` | Backend này | không |
| `db` (PostgreSQL 17) | Dữ liệu, lưu ở `data/postgres` | không |

- Cấu hình và bí mật: `/opt/ggpower/.env` (chỉ root đọc được; mẫu ở `deploy/.env.example`).
- Tệp người dùng tải lên: `data/storage`. Sao lưu DB hằng ngày lúc 02:30 vào `backups/`, giữ 14 ngày (`/etc/cron.d/ggpower-backup`).
- Cập nhật: chép mã nguồn mới vào `/opt/ggpower/backend` rồi `docker compose up -d --build`.
- Xem log: `docker compose logs -f api`. Mã OTP (khi `LOG_OTP_CODES=true`): `docker compose logs api | grep "DEV OTP"`.
- Đổi tên miền: sửa `SITE_ADDRESS` trong `.env` rồi `docker compose up -d`.
- Docker được cài dạng tệp chạy độc lập ở `/usr/local/bin` (hệ điều hành là CentOS 7, không còn kho gói); dịch vụ `docker.service`.

### Broker MQTT

- Datalogger nối tới `SITE_ADDRESS`, cổng 8883, bật TLS, **tên đăng nhập = serial biến tần**, mật khẩu riêng từng thiết bị.
- Mỗi tài khoản thiết bị chỉ gửi được `solar/ggp/inverter/<serial của nó>/up/#` và chỉ nhận được
  `.../<serial của nó>/down/#` (tệp `mqtt/config/acl`). Chỉ tài khoản `backend` gửi được lệnh.
- Backend nối với broker trong mạng nội bộ Docker (cổng 1883, không mở ra ngoài).
- Quản lý tài khoản: `./mqtt-user.sh add <serial>` (in mật khẩu mới một lần), `remove <serial>`, `list`.
- Chứng chỉ TLS được chép từ web sang broker hằng ngày bởi `mqtt-cert-sync.sh` (cron 03:15).
