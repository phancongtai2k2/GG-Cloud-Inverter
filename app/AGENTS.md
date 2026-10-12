# Quy tắc cho AI agent — App Inverter dân dụng GG Power (Android)

File này được `CLAUDE.md` nạp tự động cho Claude Code; các agent khác (Antigravity, Copilot…) đọc trực tiếp.

Đọc hết file này trước khi viết code. Khi quy tắc ở đây mâu thuẫn với yêu cầu trong một task, hãy hỏi lại thay vì tự quyết.

## 1. Nguồn thông tin và thứ tự ưu tiên

1. `docs/adr/ADR-*.md` — quyết định kiến trúc/vận hành, **ghi đè** đặc tả, ảnh và prototype ở điểm liên quan (ADR-001: demo điều khiển thật có kiểm soát; ADR-002: lịch TOU và chế độ vận hành sau 01/10/2026; ADR-003: nút quay lại về Dự án ở màn cấp dự án; ADR-004: bỏ nút quét QR ở màn Đăng nhập).
2. Mục **"Thay đổi trong phiên bản 0.6"** ở đầu `docs/spec/dac-ta-chuc-nang-v0.6.md` (viết tắt "Thay đổi 0.6 §n") — các quyết định mới nhất, **ghi đè** thân đặc tả.
3. `docs/design/screens/*.png` — giao diện đích (ảnh @2x, rộng 390 dp). Code phải trông giống ảnh.
4. `docs/design/html/*.dc.html` — giá trị chính xác: màu, cỡ chữ, khoảng cách, bo góc, text tiếng Việt, dữ liệu mẫu (khối `<script>` cuối file chứa logic và dữ liệu mẫu của màn). Phần giao diện ADR yêu cầu mà chưa có trong ảnh/HTML: theo `docs/design/BO_SUNG_v3.md`.
5. Thân `docs/spec/dac-ta-chuc-nang-v0.6.md` — hành vi, quy tắc nghiệp vụ, thanh ghi inverter. Chỗ thân đặc tả trái với mục 1–2 thì theo mục 1–2 (danh sách đã biết: `docs/spec/MAU_THUAN_v0.6.md`).
6. `docs/design/tokens/` — design tokens. `docs/design/navigation.md`, `docs/design/screens.md` — điều hướng và danh sách màn.

`docs/spec/archive/` (đặc tả v0.5, `CHANGES_sau_v0.5.md`) chỉ để tra lịch sử, **không** dùng làm nguồn.

HTML chỉ là prototype để tham khảo giá trị. **Không** nhúng WebView, không chuyển nguyên HTML sang code.

## 2. Công nghệ (mặc định — team xác nhận trước khi bắt đầu)

- Kotlin, Jetpack Compose, Material 3, một Activity, Navigation Compose.
- Kiến trúc MVVM: `ViewModel` + `StateFlow<UiState>`; UI là hàm Composable không trạng thái nhận `UiState` và callback.
- DI: Hilt. JSON: kotlinx.serialization. Mạng (sau này): Retrofit + OkHttp.
- minSdk 26, targetSdk mới nhất. Chỉ dùng thư viện AndroidX/Google/JetBrains phổ biến; **hỏi trước** khi thêm thư viện khác.
- Nếu team chọn Flutter: dùng `docs/design/tokens/flutter/gg_tokens.dart`, go_router, Riverpod; các quy tắc còn lại giữ nguyên.

## 3. Cấu trúc thư mục

```
app/src/main/java/vn/ggpower/inverter/
  ui/theme/            GgColors.kt, GgDimens.kt, Type.kt, Theme.kt
  ui/components/       Card, SectionHeader, FloatingTabBar, Segmented, Chip, Toggle, BottomSheet, Timeline24h…
  ui/<feature>/        mỗi màn: <Name>Screen.kt, <Name>ViewModel.kt, <Name>UiState.kt
  domain/              model + logic thuần Kotlin (không phụ thuộc Android)
  data/                repository interface + Fake<Name>Repository (dữ liệu mẫu)
  navigation/          AppNavGraph.kt, route
app/src/main/res/values/strings.xml      (tiếng Việt, mặc định)
app/src/main/res/values-en/strings.xml   (tiếng Anh, bổ sung sau)
```

## 4. Giao diện

- Màu, khoảng cách, bo góc **chỉ lấy từ token** (`GgColors.kt`, `GgDimens.kt`), không hard-code mã màu trong màn.
- Font Montserrat 400/500/600/700 (TTF từ Google Fonts, đặt trong `res/font`).
- Card: nền `cardBg #F9FAF7`, viền 1 dp `cardBorder #E3E8E1`, bo 16 dp, đổ bóng nhẹ. Lề màn 20 dp. Tiêu đề màn 22 sp/600. Tiêu đề mục: chữ hoa 12 sp/600, giãn chữ 0.2em, màu `inkMuted`.
- Thanh thẻ nổi (floating tab bar) ở đáy cho các màn cấp tài khoản (Dự án · Tôi) và cấp dự án (Màn hình chính · Thiết bị · Dịch vụ). Màn con không có thanh thẻ và có nút quay lại. Ba màn cấp dự án (Màn hình chính · Thiết bị · Dịch vụ) có thêm nút ← ở đầu trang để về danh sách Dự án (ADR-003); màn cấp tài khoản không có nút quay lại.
- Vùng chạm tối thiểu 48 dp. Mọi icon-button có `contentDescription` tiếng Việt.
- Hỗ trợ chế độ tối bằng token dark, nhưng ưu tiên khớp light theo ảnh trước.
- Xây component dùng chung trước, rồi mới dựng màn. Nếu hai màn có cùng mẫu (ví dụ thanh 24h, biểu đồ có thanh kéo), dùng chung một component.

## 5. Văn bản và định dạng số

- Mọi chuỗi hiển thị nằm trong `strings.xml`, **chép nguyên văn** từ HTML/ảnh (tiếng Việt có dấu).
- Viết helper định dạng dùng chung: thập phân dấu phẩy, nghìn dấu chấm (`4,2 kW`, `3.000 W`, `48.000 đ`); khoảng trắng trước đơn vị (`76 %`); thời điểm `dd/MM HH:mm` và dạng đầy đủ `HH:mm · dd/MM/yyyy` (đặc tả mục 5; mỗi màn dùng dạng như ảnh); khung giờ `HH:mm – HH:mm`; thứ viết tắt T2…T7, CN.

## 6. Tham số điều hướng

- Prototype dùng *tweak* (`role`, `strategy`, `alert`, `prefill`) để xem trạng thái khác nhau — xem `docs/design/screens.md`. Trong app, đó là tham số route (`strategyId`, `alertId`, nguồn mở màn) hoặc vai trò tài khoản, **không** phải lựa chọn hiển thị cho người dùng.
- Màn 8 mở từ 10a nhận `alertId` để điền sẵn form; nút quay lại về 10a.
- Luồng nhiều bước (12a–12d, 13a–13b, 14a–14c) là các màn riêng trong một nested navigation graph, dùng chung một ViewModel phạm vi luồng.

## 7. Dữ liệu và API

- Mỗi màn đọc dữ liệu qua repository interface. Giai đoạn này dùng `Fake…Repository` trả về dữ liệu mẫu lấy từ khối `<script>` của HTML tương ứng.
- **Không tự bịa endpoint, tên trường JSON hay mã thanh ghi.** Hợp đồng app ↔ backend: `../contract/openapi.yaml` (REST) và `../contract/realtime.md` (WebSocket). Khi cần API chưa có ở đó, dừng lại, ghi vào mục "Đang chờ" của `../contract/CHANGES.md` (và `docs/OPEN_QUESTIONS.md` nếu cần quyết định).
- App **không bao giờ** ghi trực tiếp xuống inverter. Mọi thay đổi cấu hình: hộp xác nhận "giá trị cũ → giá trị mới" → gọi backend → chỉ cập nhật UI khi backend báo ghi thành công.
- Cờ `BuildConfig.CONFIG_READ_ONLY` là công tắc chỉ xem: khi bật, mọi control cấu hình hiện giá trị nhưng bị vô hiệu. Theo ADR-001, bản demo **tắt** cờ này và chỉ cho ghi các tham số trong danh sách cho phép của ADR-001; các tham số khác vẫn bị vô hiệu.

## 8. Logic nghiệp vụ bắt buộc có unit test (JUnit, trong `domain/`)

- Kiểm tra khung trùng, **kể cả khung qua nửa đêm**; chạm mép không tính là trùng.
- Các kiểm tra của popup khung giờ (định dạng giờ, SOC 0–100, công suất 0–12.000 W, số khung tối đa) với đúng thông báo lỗi trong Thay đổi 0.6 §5, tối đa 3 khung mỗi loại (ADR-002).
- Gợi ý giờ mặc định khi thêm khung: dùng mặc định §5 nếu không trùng, không thì khoảng trống đầu tiên (ADR-002 mục 6).
- Bản nháp 16b: chặn Lưu khi chiến lược không có ngày; sinh danh sách thay đổi "cũ → mới" từ nháp so với bản đã lưu, kể cả thứ chuyển từ chiến lược khác và chiến lược bị xoá (ADR-002).
- Ánh xạ `write_access` (`state`, `writable_groups`, `blocked_groups`, `writable_modes`) và `CONFIG_READ_ONLY` sang trạng thái bật/tắt/ẩn của từng control (ADR-001, ADR-002).
- Xoá khung: khung biến mất, các khung cùng loại dồn lên và đánh số lại (Thay đổi 0.6 §4).
- Chiến lược TOU: mỗi thứ thuộc tối đa 1 chiến lược; chọn thứ đang thuộc chiến lược khác thì chuyển; tối đa 4 chiến lược; tìm chiến lược áp dụng cho một ngày cho trước.
- Sinh tóm tắt lịch cho box màn 16 và thẻ 16a (Thay đổi 0.6 §2–3): số giai đoạn, sắp theo giờ bắt đầu, thời lượng, "qua đêm", "Hằng ngày" / liệt kê thứ viết tắt.
- Quy tắc tự bật/tắt E02C/E033 theo chế độ (§16.4a của đặc tả).
- Các hàm định dạng số/thời gian ở mục 5.

## 9. Cách làm việc

- **Mỗi task một màn** (hoặc một component). Không sửa màn khác nếu task không yêu cầu.
- Trước khi code: liệt kê ngắn các phần tử của màn, trạng thái (loading / rỗng / lỗi / có dữ liệu) và tương tác, rồi mới viết.
- Sau khi dựng: chạy app hoặc Compose Preview ở kích thước 390 × 844 dp, chụp màn hình và **so với PNG thiết kế**. Liệt kê mọi chỗ khác biệt còn lại trong phần tóm tắt task.
- Mỗi màn có `@Preview` với dữ liệu mẫu.
- Build phải qua `./gradlew assembleDebug testMockDebugUnitTest testLiveDebugUnitTest lint` trước khi báo xong.
- Commit sau mỗi màn, message dạng `feat(tou): màn 16a danh sách chiến lược`.

## 10. Không được làm

- Không dựng các màn chưa có thiết kế (§3.20–§3.35 của đặc tả, trừ §3.23 = màn 10a) trừ khi task yêu cầu rõ; nếu cần chỗ nối, tạo placeholder "Đang thiết kế".
- Không dựng màn Chọn ngôn ngữ riêng (đã bỏ); ngôn ngữ chọn tại màn 3 · Tôi.
- Không tự đổi bố cục, màu, chữ so với thiết kế. Thấy thiết kế có vấn đề thì ghi vào `docs/OPEN_QUESTIONS.md`.
- Không đưa khoá API, mật khẩu, địa chỉ server thật vào code.
