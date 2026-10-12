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
2. **Client id cố định** — broker nhận lại phiên theo client id. `use_username_as_clientid` trong `mosquitto.conf`
   chỉ áp cho cổng công khai 8883; backend nối cổng nội bộ 1883 nên giữ client id của chính nó. Mặc định id đó chứa tên
   máy của container, tên này đổi mỗi lần container được tạo lại (mỗi lần deploy), nên `docker-compose.yml` đặt cố định
   `GGP_Mqtt__ClientId` cho `api` (mặc định `ggpower-backend-ingest`, đổi bằng `MQTT_CLIENT_ID` trong `.env`) và để
   trống cho `api2`. **Không** dùng client id đó cho công cụ khác (sẽ chiếm phiên của backend).
   (Sửa ngày 12/10 khi triển khai lên máy chủ thật: bản trước ghi là broker dùng tên đăng nhập làm client id.)
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

## Trạng thái dùng chung qua Redis (bước B3–B5)

Một instance: giữ mặc định `Shared__Provider=memory`. Nhiều instance: mọi instance đặt

```
GGP_Shared__Provider=redis
GGP_Shared__Redis=redis:6379          # chuỗi cấu hình StackExchange.Redis
GGP_Shared__KeyPrefix=ggp:            # đổi nếu nhiều môi trường dùng chung một Redis
```

Đi qua Redis: vé WebSocket (`ticket:*`, 30 s, dùng một lần), khoá lệnh theo thiết bị (`cmdlock:*`, tự hết sau 5 phút),
giãn cách lệnh (`cmdlast:*`), phiên bản cache thống kê (`cachever:*`), và pub/sub `rt` (frame realtime), `ack`,
`cfg` (ack và cấu hình đọc lại của lệnh). Redis mất → `/healthz/ready` báo `redis`; instance vẫn chạy, các việc trên
lỗi cho tới khi Redis về.

Lưu ý:
- Ở chế độ redis, instance nhận dữ liệu publish **mọi** frame thiết bị (không biết instance khác có ai xem): ~2 KB ×
  0,25 frame/s × số thiết bị (10.000 thiết bị ≈ 5 MB/s qua Redis).
- Live state chung (C2): instance nhận MQTT ghi hash `dev:{serial}` (khoảng 3 KB / thiết bị, giữ 30 ngày; 10.000 thiết bị
  ≈ 30 MB RAM Redis, ghi ≈ 2.500 lần/s); instance khác đọc từ đó, chậm tối đa ~0,75 s. Redis khởi động lại: đầy lại trong
  vài giây (trạng thái) tới 5 phút (cấu hình, firmware); trong lúc đó instance khác dùng bản nạp từ DB.

## File đính kèm trên S3 / SeaweedFS (bước B6)

Một instance: giữ mặc định `Storage__Provider=local` (thư mục `./data/storage`). Nhiều instance cần nơi lưu chung:
server S3 tự host **SeaweedFS** (quyết định D4, cập nhật 12/10: image MinIO không còn phát hành; code dùng chuẩn S3 nên
đổi sang AWS S3 / R2… chỉ là đổi cấu hình).

1. `.env`: đặt `S3_SECRET_KEY` (chuỗi ngẫu nhiên dài), giữ `S3_ACCESS_KEY`, `S3_BUCKET`.
2. `docker compose --profile multi up -d files` — dữ liệu ở `./data/files`; chỉ trong mạng Docker, không mở cổng ra ngoài;
   yêu cầu không có chữ ký bị từ chối.
3. Chép file cũ: `./storage-to-s3.sh` (aws-cli `s3 sync`, chạy lại được; in số file hai bên để so).
4. Cho API dùng S3:
   ```
   GGP_Storage__Provider: s3
   GGP_Storage__S3__ServiceUrl: http://files:8333
   GGP_Storage__S3__Bucket: ${S3_BUCKET:-ggpower}
   GGP_Storage__S3__AccessKey: ${S3_ACCESS_KEY:-ggpower}
   GGP_Storage__S3__SecretKey: ${S3_SECRET_KEY}
   ```
   rồi `docker compose up -d api`. Chạy lại `./storage-to-s3.sh` một lần nữa để chép file tải lên trong lúc chuyển.
5. Giữ `./data/storage` đến khi kiểm tra ảnh đại diện / ảnh dự án / file ticket mở được; sao lưu `./data/files` cùng với
   database (bổ sung vào `backup.sh` khi chuyển hẳn).

Quay lại: `GGP_Storage__Provider=local` (file tải lên sau khi chuyển chỉ có trong bucket — chép ngược bằng
`aws s3 sync s3://ggpower /storage` nếu cần).

## Hai instance API và deploy cuốn chiếu (bước B7)

Cùng một image, chia vai trò bằng `GGP_Roles` (`api`, `ingest`, `jobs`; mặc định cả ba):

| Service | Vai trò | Ghi chú |
|---|---|---|
| `api` | `api,ingest,jobs` | Nhận MQTT (phiên lưu trên broker, A5), chạy job định kỳ (leader B1), ghi live state lên Redis (C2) |
| `api2` (profile `multi`) | `api` | Chỉ phục vụ API / WebSocket; MQTT chỉ để gửi lệnh, tài khoản riêng `backend-api` (ACL chỉ ghi `down/command`) |

### Bật lần đầu

1. Tài khoản MQTT cho `api2`:
   `docker compose exec -T -u 1883:1883 mqtt mosquitto_passwd -b /mosquitto/config/passwd backend-api <mật khẩu>` rồi
   `docker compose kill -s HUP mqtt` (ACL `backend-api` đã có trong `mqtt/config/acl`). Mosquitto dùng tên đăng nhập làm
   client id, nên mỗi instance chỉ-API cần một tài khoản riêng.
2. `.env` thêm:
   ```
   SHARED_PROVIDER=redis
   STORAGE_PROVIDER=s3
   S3_SECRET_KEY=<chuỗi ngẫu nhiên dài>
   MQTT_API_USER=backend-api
   MQTT_API_PASSWORD=<mật khẩu ở bước 1>
   API_UPSTREAMS="api:8080 api2:8080"
   API_HEALTH_URI=/healthz/ready
   ```
   (Giá trị có dấu cách phải để trong ngoặc kép: `storage-to-s3.sh` nạp `.env` bằng shell.)
3. `docker compose --profile multi up -d redis files`, chép file cũ: `./storage-to-s3.sh` (mục B6).
4. `docker compose --profile multi up -d --build` — `api` khởi động lại với Redis / S3, `api2` lên, Caddy nạp upstream mới.
5. Kiểm tra: `docker compose exec web wget -qO- http://api:8080/healthz/ready` và `http://api2:8080/healthz/ready` đều
   `ready`; log `api` có `roles: api,ingest,jobs` và bốn dòng `this instance now runs the … job`; log `api2` có
   `roles: api` và `to send commands only`.

### Deploy cuốn chiếu (không gián đoạn API)

```
docker compose --profile multi build api api2
docker compose --profile multi up -d --no-deps api2
until docker compose exec -T web wget -qO- http://api2:8080/healthz/ready >/dev/null 2>&1; do sleep 2; done
docker compose --profile multi up -d --no-deps api
until docker compose exec -T web wget -qO- http://api:8080/healthz/ready >/dev/null 2>&1; do sleep 2; done
```

- Khi một instance dừng, Caddy thử request trên instance còn lại (`lb_try_duration 5s`) và ngừng gửi tới instance
  không qua `/healthz/ready` (kiểm tra mỗi 5 s). App đang nối WebSocket vào instance đó tự nối lại (vé dùng được ở instance
  nào cũng được).
- Trong lúc `api` khởi động lại: dữ liệu thiết bị chờ ở broker (A5) và được ghi khi `api` lên; job định kỳ tạm dừng
  (≤ 30 s); số live trên `api2` đứng yên ở giá trị cuối cùng trong Redis; lệnh gửi trong khoảng này trả `GATEWAY_OFFLINE`
  hoặc hết hạn chờ ack. Muốn không gián đoạn cả phần nhận dữ liệu cần nhiều instance `ingest` — giai đoạn C.
- Đổi schema (migration): instance khởi động trước áp dụng (khoá advisory, B1); bản cũ còn chạy phải chịu được schema mới —
  migration chỉ thêm, không xoá / đổi tên cột trong cùng một lần deploy.

### Quay về một instance

`.env`: bỏ `API_UPSTREAMS`, `API_HEALTH_URI` (hoặc đặt lại mặc định), giữ `SHARED_PROVIDER` / `STORAGE_PROVIDER` tuỳ ý
(một instance chạy được với cả memory / local lẫn redis / s3); `docker compose --profile multi stop api2 && docker compose up -d web`.

## Trợ lý chat (Claude)

Bản có migration `007_chat.sql` thêm trợ lý chat (`/api/v1/projects/{projectId}/chat/...`). Tắt cho đến khi có khoá:

1. Tạo API key ở console.anthropic.com (tài khoản của công ty, đặt hạn mức chi tiêu tháng ở mục Billing / Limits).
2. `.env`: `ANTHROPIC_API_KEY=...`; tuỳ chọn `CHAT_MODEL` (mặc định `claude-opus-5-5`) và `CHAT_MAX_PER_DAY` (câu hỏi mỗi
   người dùng trong 24 giờ, mặc định 50).
3. Build và khởi động lại `api` (và `api2` nếu chạy nhiều instance) như một lần deploy bình thường.

Kiểm tra: máy chủ phải ra được `api.anthropic.com:443` và `api.open-meteo.com:443` (thời tiết). Lỗi gọi model nằm trong
log với dòng `chat model call failed`; app nhận `502 CHAT_UNAVAILABLE`.

Dữ liệu: hội thoại lưu trong PostgreSQL trên máy chủ (bảng `chat_conversations`, `chat_messages`, xoá theo dự án / người
dùng). Mỗi câu hỏi gửi sang Anthropic câu hỏi đó, lịch sử hội thoại và số liệu thiết bị mà trợ lý tra (không gửi địa chỉ,
tên, email, số điện thoại của người dùng); Open-Meteo nhận toạ độ dự án làm tròn 2 chữ số thập phân (~1 km).

## Trạng thái trên máy chủ thật (triển khai 12/10/2026)

Đã bật đủ các bước A5, B1–B7, C2 theo tài liệu này: `api` (api, ingest, jobs) + `api2` (api) sau Caddy, `redis`, `files`
(SeaweedFS), migration 003–006. Bộ test chạy trên chính máy chủ trong container tách riêng trước khi chuyển: 126/126.

Những điểm riêng của máy chủ này, cần nhớ khi deploy lần sau:

- **Mọi lệnh compose phải có `--profile multi`** (`docker compose --profile multi ps | logs | up -d …`); thiếu nó thì `api2`,
  `redis`, `files` không được tính tới. Mã OTP ở chế độ thử (`LOG_OTP_CODES=true`) nằm trong log của instance đã nhận
  request: xem cả `api` và `api2`.
- **`mqtt/config/acl` trên máy chủ có thêm một khối không nằm trong kho**: tài khoản `ingestion_service`
  (`topic readwrite solar/#`), được thêm tay ngày 10/10 cho dịch vụ `ggp-solar` chạy cùng máy. Khi chép `acl` mới lên phải
  giữ khối này. Tài khoản đó ghi được cả `down/command` của mọi thiết bị.
- Broker được nạp lại cấu hình bằng `docker compose kill -s HUP mqtt` (không khởi động lại, thiết bị không rớt kết nối).
- Trên máy còn các stack khác không thuộc kho này (`/opt/ggp-solar`, `/opt/ggp-influx`, `/opt/ggp-wireguard`, frps/frpc):
  không đụng tới.
- Bản sao lưu trước khi nâng cấp: `/opt/ggpower/backups/pre-upgrade-20261012/` (dump database, `docker-compose.yml`,
  `Caddyfile`, cấu hình broker, `.env`, mã nguồn cũ) và image `ggpower-api:before-upgrade-20261012`.
- `backup.sh` chưa sao lưu `./data/files` (file đính kèm trên SeaweedFS): bổ sung khi bắt đầu có file thật.
- Bản sửa "dừng instance khi có app đang nối" (LOADTEST.md, mục đo trên máy chủ thật) đã lên cùng ngày 12/10: build lại
  `api` và `api2`, thay `api2` trước, chờ `/healthz/ready`, rồi `api` (mỗi instance ~3 giây, không mất mẫu nào của
  thiết bị thật). Bản trước đó: image `ggpower-api:before-wsfix-20261012`, `ggpower-api2:before-wsfix-20261012` và file
  nguồn cũ trong `backups/pre-wsfix-20261012/`.
- Thiết bị thử do người khác gửi lên broker (serial lạ dưới tenant đang nhận) được backend tự đăng ký vào bảng `devices`.
  Sáu thiết bị kiểu đó của ngày 10/10 đã được gỡ ngày 12/10; các dòng của chúng lưu ở `backups/fake-devices-20261012/`.

Quay về bản trước nâng cấp (schema mới chỉ thêm cột / index nên bản cũ chạy được):

```
cd /opt/ggpower
docker compose --profile multi stop api2
cp backups/pre-upgrade-20261012/docker-compose.yml backups/pre-upgrade-20261012/Caddyfile .
docker tag ggpower-api:before-upgrade-20261012 ggpower-api:latest
docker compose up -d --no-build api web
```
