# GG Power – App inverter dân dụng (Android) · Gói thiết kế cho Claude Code

Gói này chứa **toàn bộ thiết kế đã chốt** của app. Nhiệm vụ: dựng app Android native **đúng từng pixel** theo thiết kế, Light + Dark mode.

> Nếu repo đã có code (ví dụ `HistoryCard.kt`, `BatteryDataScreen.kt`, tài liệu `BO_SUNG_v3`, `ADR-001` được nhắc trong comment thiết kế), hãy đọc code hiện có trước và **cập nhật UI theo thiết kế này**, không viết lại logic nghiệp vụ / tầng dữ liệu.

## 1. Cấu trúc gói

```
CLAUDE.md                 ← file này (đọc trước)
design/
  screens/*.dc.html       ← 63 artboard: X.dc.html = Light, XDark.dc.html = Dark
  canvas.json             ← mục lục: tên màn hình, số hiệu, chiều cao artboard, thứ tự
  tokens.json             ← design system GG Power (màu, chữ, spacing, radius, shadow)
  theme/Color.kt          ← bảng màu Compose đã trích sẵn (Light/Dark)
  assets/                 ← logo_gg_power.png (Login), explore_banner.jpg (Khám phá)
  DC_FORMAT.md            ← mô tả định dạng .dc.html (tham khảo)
```

## 2. Cách đọc file `.dc.html`

- Mỗi file là **một màn hình 390 px rộng** (1 px thiết kế = 1 dp). Chiều cao > 844 nghĩa là màn hình cuộn.
- Toàn bộ style là **inline CSS** → đó là spec chính xác (màu hex, cỡ chữ, line-height, padding, radius, shadow). Không đoán, hãy lấy số từ file.
- Cú pháp template: `{{bien}}` = giá trị động; `<sc-if value="{{x}}">` = hiển thị có điều kiện; `<sc-for list="{{xs}}" as="x">` = lặp danh sách.
- `<script type="text/x-dc">` chứa `class Component extends DCLogic` → `renderVals()` mô tả **state, dữ liệu mẫu và hành vi** (toggle, validate, toast 2 s, kéo thả…). Dùng nó làm spec cho ViewModel/state.
- `data-props` = các biến thể trạng thái cần hỗ trợ (ví dụ vai trò A/B/C, trạng thái ghi, `sky` day/night).
- Các `href="X.dc.html"` = điều hướng giữa màn hình (xem mục 5).
- Chuỗi dạng `[SỐ SERIAL]`, `[PHIÊN BẢN]`, `[GIÁ]`, `[TÊN HÃNG]`, `[MÃ BIT]`, `[DUNG LƯỢNG]`… là **placeholder chờ dữ liệu thật** – nối API, không hard-code.

## 3. Công nghệ đề xuất

Kotlin + Jetpack Compose + Material 3, Navigation Compose, ViewModel + StateFlow. minSdk 26. Font **Montserrat** (400/500/600/700, Google Fonts / bundle `res/font`). Ngôn ngữ UI: tiếng Việt (đưa chuỗi vào `strings.xml`). Hỗ trợ theme theo hệ thống + chuyển tay.

## 4. Quy tắc giao diện chung (đã chốt qua các vòng review – bắt buộc giữ)

**Màu**: dùng `design/theme/Color.kt`. Tóm tắt:

| Vai trò | Light | Dark |
|---|---|---|
| Nền trang | `#f5f7f3` | `#0c1612` |
| Card | `#f9faf7` + shadow nhẹ | `#15221d` |
| Nổi (dialog, bottom nav, sheet) | `#ffffff` | `#1c2b25` |
| Chữ chính / phụ | `#13201b` / `#56625c` | `#eef3ef` / `#9fada6` |
| Primary (chữ, chọn) | `#0f7a3a` | `#34c26f` |
| Primary fill (nút) | `#0f7a3a` | `#178a47` |
| Primary soft | `#e3f3e8` | `#12301f` |
| Cam (PV / xả) | `#e0700f` | `#f7963a` |

- **Card không có viền**; tách khối bằng màu nền + shadow (Light) / màu nền (Dark).
- **Không có đường kẻ phân cách giữa các dòng** trong danh sách/bảng. Ngoại lệ được giữ: đường kẻ trên footer màn Tài khoản, gridline nét đứt của biểu đồ, và vạch "hoặc nhập tay" ở Thêm dự án.
- **Bottom navigation** dạng viên thuốc nổi (bo 999): chỉ tab đang chọn hiện chữ, các tab khác chỉ icon.
- Icon trong các khối tổng quan dùng **cùng một màu xanh primary**.
- **Màu TOU**: Sạc = xanh (`#149c4a` light / `#34c26f` dark); Xả = cam (`#e0700f` / `#f7963a`). Áp dụng cho Config, TouSchedule, TouStrategy.
- Toast/snackbar là **lớp nổi (overlay)**, không đẩy layout. Ví dụ màn Dịch vụ: viên thuốc nổi cách đáy 104 dp, nền `rgba(19,32,27,0.62)` (light) / `rgba(238,243,239,0.66)` (dark), blur 8, tự ẩn sau 2 s.
- Touch target ≥ 48 dp; radius: chip 6, nút/input 10, card 16, pill 999.

## 5. Danh sách màn hình & điều hướng

| # | File (Light) | Màn hình | Đi tới |
|---|---|---|---|
| 1 | Login | Đăng nhập | ForgotPassword, Register, Projects |
| 12a–d | ForgotPassword, ForgotPasswordOtp, ForgotPasswordNew, ForgotPasswordDone | Quên mật khẩu (tìm TK → OTP → mật khẩu mới → xong) | tuần tự, về Login |
| 13a–b | Register, RegisterOtp | Đăng ký + OTP | ProjectsEmpty |
| 2 | Projects / ProjectsEmpty | Danh sách dự án / trạng thái rỗng | AddProject, Main, Me |
| 14a–c | AddProject, AddProjectInfo, AddProjectTariff | Thêm dự án: thiết bị → thông tin → giá điện | QRScan, Main |
| 18 | QRScan | Thêm thiết bị bằng QR | AddProject |
| 3 | Me | Tôi | Account, Login |
| 15 | Account | Thông tin tài khoản | Me |
| 4 | Main | Màn hình chính (tab) | Alerts, BatteryEnergy, Config, Device, Service |
| 16 / 16a / 16b | Config, TouSchedule, TouStrategy | Cấu hình dự án, lịch TOU, chi tiết chiến lược | |
| 10 / 10a | Alerts, AlertDetail | Cảnh báo, chi tiết | Support |
| 11 | BatteryEnergy | Nguồn năng lượng pin | Main |
| 5 | Device | Thiết bị (tab) | DataInverter, DataBattery |
| 17 | DataInverter, DataBattery | Theo dõi dữ liệu inverter / pin | Device |
| 6 | Service | Dịch vụ (tab) | Explore, Support, Warranty |
| 7 | Explore | Khám phá | |
| 8 | Support, TicketDetail | Yêu cầu hỗ trợ, chi tiết phiếu | SupportSent |
| 9 | Warranty | Bảo hành | Support |
| 24 | SupportSent | Gửi yêu cầu thành công | |

Bottom nav (trong một dự án, 3 tab): Màn hình chính (Main) · Thiết bị (Device) · Dịch vụ (Service). Bản Dark của mỗi màn hình là `XDark.dc.html` (Main có thêm `MainDarkNight.dc.html`).

## 6. Ghi chú chi tiết các màn hình phức tạp

**4 · Main** (`Main.dc.html`, `MainDark.dc.html`, `MainDarkNight.dc.html`)
- Sơ đồ dòng năng lượng dạng **ngôi nhà vector** (SVG 700×690, scale 0.5 trong khung cao 345) có tia sáng, đường dòng chạy, chip CO₂ trôi nhẹ. Nhãn: **Lưới, Pin, Tải** (PV). Chuyển SVG sang Compose `Canvas`/ImageVector hoặc dùng `AndroidView` + VectorDrawable; giữ animation.
- Đường nối nét đứt dọc từ các chỉ số tới sơ đồ; đường inverter → tải màu cam.
- Nút 4 ô vuông mở bottom sheet **"Tuỳ chỉnh màn hình chính"**: carousel ngang chọn kiểu sơ đồ (Hình nhà / Sơ đồ khối), không hiện scrollbar, tự cuộn tới kiểu đang chọn; danh sách widget **kéo-thả để sắp xếp** (tay nắm ⋮⋮), nút trừ để ẩn, dấu + xanh để thêm. Widget tuỳ chỉnh được: `sum`, `pw`, `yl`, `bat`. Card **"Chế độ & pin" cố định** (luôn ở vị trí 5, không ẩn được). Lưu thứ tự vào DataStore.
- Dark mode: nền trời phía sau sơ đồ – ban ngày gradient vàng `#5e5a45` mờ dần 2 đầu; ban đêm có sao, trăng, sao băng. Tham số `sky = auto|day|night` (auto theo giờ địa phương).

**11 · BatteryEnergy**: khối pin 3D isometric, mức SOC (76 %) màu xanh; **2 gạch nét đứt giới hạn** sạc 95 % / xả 10 % (giá trị lấy từ cấu hình BMS); khi mở màn hình mặt chất lỏng **sóng sánh** tắt dần ~2,6 s (xem `<animate>` trong SVG).

**17 · DataInverter / DataBattery**: không còn tab – **tất cả nhóm dữ liệu xếp dọc**, mỗi nhóm có tiêu đề in hoa nhỏ + 1 card các dòng key/value. Inverter: Tổng quan · PV · Lưới & tải · Nhiệt độ. Pin: Tổng quan · Giới hạn · Cell · Bảo vệ (kèm thẻ cờ BMS, nút "Xem tất cả trạng thái" mở 64 cờ) · Phiên bản. Không có biểu đồ lịch sử.

**5 · Device**: có card **Datalogger Wi-Fi** (Tín hiệu, Kết nối, Firmware).

**16 · Config**: dialog đổi chế độ hiển thị mô tả chế độ đích; quyền theo vai trò A/B/C và trạng thái ghi (xem `data-props`).

## 7. Cách làm việc đề xuất

1. Dựng `Theme.kt` từ `design/theme/Color.kt` + typography Montserrat (xem `tokens.json > type`).
2. Dựng bộ component dùng chung: TopBar (back 48 dp + tiêu đề 22/28 600), Card, KeyValueRow, StatusChip, PrimaryButton (52 dp, bo 10), OutlinedButton, TextField 48 dp, SegmentedControl, FloatingPillBottomNav, Dialog, Toast overlay.
3. Dựng từng màn hình theo thứ tự bảng mục 5, so sánh với file `.dc.html` Light **và** Dark.
4. Mở file `.dc.html` trong trình duyệt chỉ để tham khảo cấu trúc; nguồn sự thật là style inline + `renderVals()`.
