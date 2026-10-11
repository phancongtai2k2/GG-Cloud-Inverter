# Tiến độ — làm lại giao diện app & nâng cấp backend

Cập nhật: 11/10/2026 · Nhánh: `feature/contract-and-theme` (9 commit trên `main`, chưa push, chưa tạo PR)

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
| (commit này) | Config: hộp xác nhận có ô "Cách hoạt động của …" / "Sau khi thay đổi", thẻ chế độ bỏ viền, thanh SOC khoá màu `sliderDisabled` + mờ khi chưa có BMS, bỏ hẳn mã thanh ghi (xoá 22 chuỗi `reg_*`). TOU 16a/16b: đối chiếu xong, các mục mới đã có từ BO_SUNG_v3 |

Trạng thái kiểm tra cuối: `./gradlew assembleDebug testMockDebugUnitTest testLiveDebugUnitTest :chart:testDebugUnitTest lint` **xanh**; backend `dotnet test` **93/93**.

## 4. Việc tiếp theo (theo thứ tự)

### 4.1 App — Config / TOU: **xong** (xem bảng mục 3)
Còn lại nhỏ: màn Dữ liệu inverter/pin vẫn truyền chuỗi `reg_*` vào `DataRow.register` dù không hiển thị (46 chuỗi) — dọn khi tiện.

### 4.2 App — Main (màn 4, khó nhất, khác biệt 2.371 dòng)
- Sơ đồ ngôi nhà vector (SVG 700×690 scale 0,5) → Compose Canvas, giữ animation tia sáng / dòng chạy / chip CO₂ trôi; đường nét đứt nối chỉ số; inverter → tải màu cam.
- Dark: nền trời ngày (gradient `DarkDaySky` #5E5A45) / đêm (sao, trăng, sao băng); tham số `sky = auto|day|night`.
- Bottom sheet "Tuỳ chỉnh màn hình chính": carousel chọn kiểu sơ đồ (Hình nhà / Sơ đồ khối), danh sách widget `sum`, `pw`, `yl`, `bat` kéo-thả + ẩn/hiện, card "Chế độ & pin" cố định vị trí 5; lưu DataStore.
- Tab Ngày/Tháng/Năm + kỳ trước/sau đặt trên biểu đồ đầu; biểu đồ thu phóng (dùng `chartPinchZoom` + `ZoomResetPill`, maxZoom 8).
- Icon khối tổng quan cùng màu `primary`; ô "Dung lượng sạc hôm nay" theo quyết định ở mục 2 (mock trước).

### 4.3 Backend — trường mới cho thiết kế (xem bảng "Đang chờ" trong `contract/CHANGES.md`)
Datalogger (serial, rssi→mức, kiểu kết nối, firmware — `signal_rssi`/`ip_addr` đã có trong `up/status`), giới hạn SOC sạc/xả (cần firmware), CO₂ hôm nay + model trong `/overview`, sạc hôm nay theo nguồn (`rollup_15m.pv_charge_kwh`). Mỗi trường: sửa `openapi.yaml` → thêm mục `CHANGES.md` → code + test → nối app (bỏ "—").

### 4.4 Backend — nâng cấp để mở rộng
- Nhanh: index `upper(serial)`; `/healthz` kiểm tra DB + tách liveness/readiness; metrics (OpenTelemetry/Prometheus); rollup chỉ cho thiết bị có dữ liệu mới (bỏ quét 3 ngày mọi thiết bị mỗi 30 s); MQTT persistent session + client id cố định; ghi status/alarm qua hàng đợi thay vì trong handler MQTT.
- Trung bình: vé realtime / khoá lệnh / cache → Redis; WebSocket fan-out qua pub/sub; file upload → object storage (S3/MinIO); leader election cho timer (rollup, báo cáo ngày, TOU 00:00, offline monitor); `AlertStore.RaiseAsync` thành upsert trong transaction.
- Lớn: tách worker ingest khỏi API (`$share/` subscription), cân nhắc TimescaleDB, xác thực MQTT động; CI; thay CentOS 7, build image ở CI, backup ngoài máy.

## 5. Môi trường trên máy này

- **Backend test**: máy chỉ có .NET 10 → chạy với roll-forward; PostgreSQL trong Docker:
  ```
  docker start ggpower-pg-test        # lần đầu: docker run -d --name ggpower-pg-test -e POSTGRES_USER=ggpower -e POSTGRES_PASSWORD=devtest -e POSTGRES_DB=ggpower_test -p 5433:5432 postgres:17-alpine
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
