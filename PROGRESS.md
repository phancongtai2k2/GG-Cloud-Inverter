# Tiến độ — làm lại giao diện app & nâng cấp backend

Cập nhật: 11/10/2026 · Nhánh: `feature/contract-and-theme` (16 commit trên `main`, chưa push, chưa tạo PR)

## 1. Mục tiêu

1. Cập nhật giao diện app Android theo thiết kế mới `app/gg-inverter-design/` (31 màn, Light + Dark).
2. Nâng cấp backend .NET để sau này chạy được nhiều instance và quy mô lớn.
3. Đồng bộ app ↔ backend qua hợp đồng chung `contract/` (file bàn giao: `contract/CHANGES.md`).

## 2. Quyết định đã chốt

| Ngày | Quyết định |
|---|---|
| 11/10 | Tạo `contract/` (OpenAPI + realtime + CHANGES) làm nguồn sự thật chung; backend có `ContractTests` giữ đồng bộ |
| 11/10 | Ô "Dung lượng sạc hôm nay (Từ PV / Từ lưới)" ở màn chính = **kWh đã sạc vào pin trong ngày theo nguồn** (khác `battery_content`) |
| 11/10 | Làm theo thứ tự: nhóm màn đơn giản → Thiết bị → Config/TOU → Main; mỗi màn so Light + Dark trên emulator |
| 11/10 | Màn Dữ liệu inverter/pin: **bỏ biểu đồ lịch sử** theo thiết kế (giữ API `/history` và domain để dùng lại) |
| 11/10 | Trường mới cho thiết kế (Datalogger, giới hạn SOC…): **làm giao diện trước** với dữ liệu mock, bản live hiện "—"; backend làm sau |
| 11/10 | Thư mục thiết kế `app/gg-inverter-design/` được commit vào repo |
| 11/10 | Mọi điểm cần người dùng quyết: hỏi bằng hộp chọn (popup) rồi mới làm |
| 11/10 | Main: nền trời dark `auto` = giờ máy 6h–18h là ngày; tuỳ chỉnh màn chính lưu **chung mọi dự án** (DataStore); làm Main trong 3 commit: sơ đồ nhà → nền trời → bottom sheet + tab kỳ + thu phóng |
| 11/10 | Datalogger: API trả rssi / ip / firmware ngay; serial đọc `logger_sn`, kiểu kết nối đọc `conn_type` khi firmware gửi (chưa có: serial null, kết nối "wifi"). Vạch giới hạn SOC: xả = `battery_reserve_soc` (SOC dự phòng), sạc đọc `charge_stop_soc` khi firmware gửi (chưa có: null) |
| 11/10 | Nâng cấp 4.4: metrics dùng OpenTelemetry + Prometheus (D1); broker giữ bản tin cho backend ≈ 10 phút ở 5.000 thiết bị, `max_queued_messages` ≈ 800.000 (D2) |
| 12/10 | Nâng cấp 4.4 giai đoạn B: Redis = container `redis:7` trong compose (D3); file đính kèm lên MinIO tự host, giữ lưu ổ đĩa cho dev (D4) |
| 12/10 | B5: instance API thứ 2 lấy dữ liệu live theo kế hoạch — frame realtime, ack lệnh, config đọc lại đi qua Redis pub/sub (không cho mọi instance nghe MQTT). Hệ quả: REST live và gửi lệnh trên instance không nhận MQTT cần live state chung → **làm C2 (live state lên Redis) trước B7** |
| 12/10 | D4 cập nhật: image MinIO không còn phát hành (Docker Hub, quay.io) → lưu file trên **SeaweedFS** (S3 tự host, Apache 2.0); code dùng chuẩn S3 nên đổi nhà cung cấp chỉ là cấu hình |

## 3. Đã xong

| Commit | Nội dung |
|---|---|
| 77611cd | Thêm bộ thiết kế mới vào repo |
| f881ec9 | `contract/openapi.yaml` (55 thao tác), `realtime.md`, `CHANGES.md`; backend `ContractTests` (93/93 test xanh) |
| 52161f8 | Theme: bảng màu `GgPalette` từ `Color.kt` mới, `primaryFill`, dark có bóng, **TOU sạc xanh / xả cam**, card bỏ viền, h1–h3 |
| 196385f | Thanh tab mới (chỉ tab chọn có chữ), bỏ đường kẻ giữa các dòng, `GgToast` lớp nổi |
| 0ea643b | Login, Quên MK, Đăng ký, Dự án, Tôi, Tài khoản; `GgToggle` màu mới |
| 4288957 | Thêm dự án, QR, Dịch vụ, Hỗ trợ, Bảo hành, Cảnh báo; **thu phóng biểu đồ** (`chart/ChartZoom.kt`, `ZoomResetPill`) dùng ở AlertDetail |
| 6f1c410 | Thiết bị: thẻ Datalogger Wi-Fi (`DataloggerSummary`, `SignalLevel.fromRssi`) |
| c0490b4 | Dữ liệu inverter/pin: nhóm xếp dọc (`DataSection`), bỏ tab + biểu đồ lịch sử; `LiveController` bỏ khái niệm nhóm |
| 389b72f | Năng lượng pin: khối 3D một màu theo SOC, sóng sánh 2,6 s, vạch giới hạn sạc/xả, dung lượng khả dụng = SOC × định mức |
| 73dd9d7 | Config: hộp xác nhận có ô "Cách hoạt động của …" / "Sau khi thay đổi", thẻ chế độ bỏ viền, thanh SOC khoá màu `sliderDisabled` + mờ khi chưa có BMS, bỏ hẳn mã thanh ghi (xoá 22 chuỗi `reg_*`). TOU 16a/16b: đối chiếu xong, các mục mới đã có từ BO_SUNG_v3 |
| 3a83f91 · 3e24033 · 703ad15 · 5487056 | Main: sơ đồ ngôi nhà (hình sinh từ SVG), nền trời dark, tuỳ chỉnh màn hình chính + sơ đồ khối mới + thẻ Chế độ & pin, thu phóng biểu đồ (chi tiết mục 4.2) |

Trạng thái kiểm tra cuối (sau Main 3b): `./gradlew assembleDebug testMockDebugUnitTest testLiveDebugUnitTest :chart:testDebugUnitTest lint` **xanh**; backend `dotnet test` **94/94**. **Xong phần app và 4.3; việc tiếp theo là 4.4 (nâng cấp backend để mở rộng).**

## 4. Việc tiếp theo (theo thứ tự)

### 4.1 App — Config / TOU: **xong** (xem bảng mục 3)
Còn lại nhỏ: màn Dữ liệu inverter/pin vẫn truyền chuỗi `reg_*` vào `DataRow.register` dù không hiển thị (46 chuỗi) — dọn khi tiện.

### 4.2 App — Main (màn 4): **xong** (4 commit: 3a83f91, 3e24033, 703ad15 + commit thu phóng)
- ~~Sơ đồ ngôi nhà~~ **xong** (commit 1/3): `ui/home/house/` — hình tĩnh SINH bằng `app/tools/svg2kt.mjs` (`node app/tools/svg2kt.mjs <Main.dc.html> <HouseArt.kt>`), vệt sáng 5 lớp theo `<animate>`, nhãn, chip CO₂ trôi. Mock theo số thiết kế (lưới 0,5, pin 1,6); `HomeOverview.co2TodayKg` null ở live. `EnergyFlowDiagram` cũ giữ cho kiểu "Sơ đồ khối" (commit 3).
- ~~Nền trời dark~~ **xong** (commit 2/3): `SkyBackdrop` + `domain/home/Sky.kt` (6h–18h là ngày, 46 sao sinh như prototype, có test).
- ~~Tuỳ chỉnh màn hình chính~~ **xong** (commit 3a): `HomeCustomizeScreen` phủ toàn màn (carousel bắt dính giữa, kéo tay nắm đổi chỗ, ẩn/thêm, Áp dụng), `domain/home/HomeLayout.kt` (+ test) lưu trong `SettingsStore`; "Sơ đồ khối" làm lại theo thiết kế mới (`NodesFlowDiagram`, vệt sáng dùng chung `FlowComet.kt`); thẻ "Chế độ & pin" hai ô cạnh nhau, cố định dưới sơ đồ.
- ~~Tab Ngày/Tháng/Năm đặt trên biểu đồ đầu~~ **xong** (3a). ~~Biểu đồ thu phóng~~ **xong** (3b): `CartesianChart` / `StackedBarChart` nhận `zoom: ChartZoomState` (cửa sổ x, cắt vùng vẽ, nhãn `zoomTicks`, `barWindow`); Công suất / Pin ngày tối đa ×8, Sản lượng PV ×4 / ×8; nút `ZoomResetPill` ở tiêu đề. Biểu đồ Tháng/Năm dạng đường chưa phóng (thiết kế không có).
- ~~Icon tổng quan `primary`; ô "Dung lượng sạc hôm nay"~~ **xong** (3a): `HomeOverview.chargedToday` (mock 10,2 / 1,9 kWh, live "—").

### 4.3 Backend — trường mới cho thiết kế: **xong** (3 commit, xem `contract/CHANGES.md` các mục 2026-10-11)
`/overview`: model, co2_today_kg, battery_charged_today (cả frame `device`, làm mới bởi RollupService); Datalogger ở `/devices` + snapshot; `BatteryLive` giới hạn SOC sạc/xả; `BMS_NOT_CONNECTED` chặn SOC dự phòng. App (live) đã đọc hết. **Còn phía firmware**: `logger_sn`, `conn_type`, `charge_stop_soc` (backend đọc sẵn). Backend test 94/94.

### 4.4 Backend — nâng cấp để mở rộng (đang làm)
**Đặc tả và trình tự: `backend/docs/KE_HOACH_NANG_CAP_BACKEND.docx`** (đọc bằng `pandoc -t plain <file>`; sinh lại bằng `backend/tools/docgen/ke-hoach-nang-cap.js`). Làm đúng thứ tự, mỗi bước một commit, quyết định hỏi bằng popup rồi ghi vào mục 2. Cập nhật bảng dưới sau mỗi bước.

| Bước | Nội dung | Trạng thái | Commit |
|---|---|---|---|
| A0 | Bài thử tải `tools/GgPower.LoadGen`, số nền → `docs/LOADTEST.md` | **Xong**: ingest dư (5.000 tb = 13 % một lõi); tổng hợp 2,9 / 5,9 / 11,6 s ở 1k / 2k / 5k tb → trần ~10–12k; ≈ 3,2 MB/tb/ngày | 1f9415c |
| A1 | Index `upper(serial)` | **Xong**: migration 003, test EXPLAIN | ff4e260 |
| A2 | `/healthz/live`, `/healthz/ready` | **Xong**: ready kiểm DB ≤ 2 s, MQTT, hàng đợi < 80 %; Caddy dùng ở B7 | d714134 |
| A3 | Metrics OpenTelemetry → `/metrics` (D1) | **Xong**: meter `GgPower` (ingest, ghi lô, tổng hợp, lệnh, kết nối, thiết bị) + HTTP; `docs/METRICS.md` | 21aba2b |
| A4 | Tổng hợp tăng dần (chỉ thiết bị / ngày có dữ liệu mới) | **Xong**: 5.000 tb 11,6 s → 2,4 s mỗi lần, PG 49 % → 27 % (`RollupTracker`, nhìn lùi 1 phút, ngày theo hiệu số, fillfactor 70; quét đầy đủ khi khởi động + mỗi 6 h) | 08b2a46 |
| A5 | MQTT persistent session, hàng đợi broker (D2) | **Xong**: tắt 61 s ở 1.000 tb → đủ mẫu; ghi nốt hàng đợi khi dừng; bỏ qua mẫu cũ / lặp khi giao lại | 10ca20b |
| A6 | Ghi status / cảnh báo / config-state qua hàng đợi | **Xong**: `DeviceEventQueue` (một luồng ghi, giữ thứ tự, thử lại, ghi nốt khi dừng); status / firmware có thể bỏ khi đầy, cảnh báo không | 9d6e504 |
| A7 | Đo lại, chốt giai đoạn A | **Xong**: 10.000 tb không mất dữ liệu (sửa ghi trạng thái thiết bị một câu lệnh), tổng hợp 7 s / 30 s; trần ước ~20.000 tb | 7e8060a |
| B0 | Chốt Redis, nơi lưu file (D3, D4) | **Xong**: `redis:7` trong compose; MinIO tự host | — |
| B1 | Leader cho job định kỳ | **Xong**: `JobLeader` (advisory lock trên kết nối riêng): rollup, báo cáo ngày (+ bảng `daily_reports_sent`), cảnh báo offline, lệnh (TOU 00:00, đóng lệnh bỏ dở sau 5 phút thay vì lúc khởi động); metric `ggpower_jobs_leader` | fc18abc |
| B2 | Tạo cảnh báo an toàn khi chạy song song | **Xong**: migration 006 gộp cảnh báo mở trùng + unique `(serial, code) WHERE ended_at IS NULL`; RaiseAsync trong transaction có khoá theo thiết bị + mã; test 20 lần song song → 1 cảnh báo | 67b65f8 |
| B3 | Interface cho trạng thái dùng chung | **Xong**: `ITicketStore`, `ICommandCoordinator` (khoá lệnh, giãn cách, chờ ack), `ICacheVersions`, `IRealtimeBus`, `IFileStore`; bản trong RAM / ổ đĩa, chọn bằng `Shared:Provider` (hiện chỉ `memory`) | 2f926f1 |
| B4 | Bản Redis cho vé, khoá lệnh, giãn cách, phiên bản cache | **Xong**: `Shared:Provider=redis` (GETDEL vé, SET NX PX khoá + mở khoá đúng chủ, INCR phiên bản cache); ready kiểm Redis; test 2 kết nối = 2 instance | e9abcb5 |
| B5 | Frame realtime, ack lệnh, config đọc lại qua Redis pub/sub | **Xong**: kênh `rt`, `ack`, `cfg`; lệnh đọc lại cấu hình qua `ICommandCoordinator` (không còn đọc live state); test 2 host: WebSocket ở B nhận frame đưa vào A, lệnh gửi ở B xác nhận qua A, hai host không cùng ra lệnh một thiết bị | f8ac022 |
| C2 (đưa lên) | Live state lên Redis — cần trước B7 (quyết định B5) | **Xong**: `ILiveStore`; instance nhận dữ liệu ghi hash `dev:{serial}` (s / c / f, gộp mỗi 250 ms, ghi đủ mỗi 5 phút); instance khác đọc Redis (cache 500 ms); bản sao chỉ nạp từ DB không bao giờ ghi đè (`Fed`); gỡ cảnh báo ở instance nào cũng đếm lại ở nơi nhận dữ liệu; test /overview ở B từ dữ liệu vào A | (commit kế tiếp ghi) |
| B6 | File đính kèm lên S3 (SeaweedFS, D4 cập nhật) | **Xong**: `S3FileStore` (`Storage:Provider=s3`), giữ `LocalFileStore`; compose profile `multi` có `redis`, `files`; `storage-to-s3.sh`; test ticket có ảnh tạo ở A xem ở B | 9b2731b |
| B7 | 2 instance, deploy cuốn chiếu (sau C2) | Chưa làm | |
| C1–C6 | Tách vai trò, live state lên Redis, EMQX + nhiều worker, TimescaleDB, xác thực MQTT, vận hành (D5–D8) | Chưa làm | |

## 5. Môi trường trên máy này

- **Backend test**: máy chỉ có .NET 10 → chạy với roll-forward; PostgreSQL trong Docker:
  ```
  docker start ggpower-pg-test        # lần đầu: docker run -d --name ggpower-pg-test -e POSTGRES_USER=ggpower -e POSTGRES_PASSWORD=devtest -e POSTGRES_DB=ggpower_test -p 5433:5432 postgres:17-alpine
  docker start ggpower-redis-test     # từ B4; lần đầu: docker run -d --name ggpower-redis-test -p 6380:6379 redis:7-alpine
  docker start ggpower-s3-test        # từ B6 (SeaweedFS); lần đầu (Git Bash: MSYS_NO_PATHCONV=1): docker run -d --name ggpower-s3-test -p 8334:8333 chrislusf/seaweedfs:3.80 server -s3 -dir=/data
  cd backend
  DOTNET_ROLL_FORWARD=Major GGP_ConnectionStrings__Main="Host=localhost;Port=5433;Database=ggpower_test;Username=ggpower;Password=devtest" dotnet test tests/GgPower.Tests
  ```
  (`run.ps1` cần `%LOCALAPPDATA%\ggpower-dev\secrets.env`, máy này chưa có.) Docker Desktop phải đang chạy.
- **App**: `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`; cổng kiểm tra đầy đủ:
  `./gradlew assembleDebug testMockDebugUnitTest testLiveDebugUnitTest :chart:testDebugUnitTest lint`
- **Emulator**: AVD `Medium_Phone` (1080×2400, mật độ 2,625). Bản mock đăng nhập bằng `demo@ggp.vn` / mật khẩu mẫu trong `FakeAuthRepository.SAMPLE_PASSWORD`. Đổi dark: `adb shell cmd uimode night yes|no`.
- Toạ độ chạm hay dùng (px thật): thẻ dự án đầu (540, 560); tab giữa (670, 2195); tab phải (900, 2195); tab Tôi ở danh sách dự án (864, 2196).

## 6. Ghi chú / nợ kỹ thuật

- Domain còn code không UI dùng nữa: `currentLimitState`, `voltageDeviationHigh` (màn pin bỏ đồng hồ/hộp lệch) — giữ tạm vì có test; quyết định xoá khi chốt.
- Màu Theme còn một số ô "prototype cũ" chưa có trong thiết kế mới (flow, QR…) — xử lý khi làm lại Main/QR nếu thiết kế đổi.
- Backdrop blur của toast (blur 8) chưa làm — Compose chưa hỗ trợ trên mọi phiên bản Android.
- `app/docs/design/` (thiết kế v3 cũ) vẫn giữ để diff; khi xong toàn bộ có thể chuyển vào `archive/` và cập nhật `app/AGENTS.md` trỏ sang thiết kế mới.
- Commit message có `Co-Authored-By: Claude Opus 5.5`. Chưa push nhánh, chưa mở PR.
