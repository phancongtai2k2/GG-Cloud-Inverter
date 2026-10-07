# Danh sách màn hình

Mỗi màn có ảnh thiết kế (`screens/*.png`, @2x, rộng 390 dp), HTML nguồn (`html/*.dc.html`) và mục tương ứng trong đặc tả (`../spec/dac-ta-chuc-nang-v0.6.md`). "Thay đổi 0.6 §n" là mục n của phần "Thay đổi trong phiên bản 0.6" ở đầu đặc tả.

Khung thiết kế 390 × 844 dp; màn dài hơn là màn cuộn dọc. Ảnh có hậu tố `__…` là trạng thái phụ của cùng màn.

Tổng: 30 màn, 42 ảnh.

| Số | Màn | Ảnh | HTML | Route đề xuất | Cấp điều hướng | Tài liệu | Đợt | Ghi chú |
|---|---|---|---|---|---|---|---|---|
| 1 | Đăng nhập | [Login.png](screens/Login.png) | `html/Login.dc.html` | `login` | Ngoài | §3.1 + ADR-004 | 1 | Đăng nhập email/SĐT + mật khẩu. Link Quên mật khẩu, Đăng ký. Không có nút Thêm thiết bị bằng QR (ADR-004; ảnh còn nút này). |
| 12a | Quên mật khẩu – Tìm tài khoản | [ForgotPassword.png](screens/ForgotPassword.png) | `html/ForgotPassword.dc.html` | `forgot_password` | Ngoài | §3.12 | 3 | Bước 1/3 · Tìm tài khoản: nhập email/SĐT → Gửi mã xác thực. |
| 12b | Quên mật khẩu – Mã xác thực | [ForgotPasswordOtp.png](screens/ForgotPasswordOtp.png) | `html/ForgotPasswordOtp.dc.html` | `forgot_password/otp` | Ngoài | §3.12 | 3 | Bước 2/3 · Nhập mã 6 số, đếm ngược gửi lại mã. |
| 12c | Quên mật khẩu – Mật khẩu mới | [ForgotPasswordNew.png](screens/ForgotPasswordNew.png) | `html/ForgotPasswordNew.dc.html` | `forgot_password/new` | Ngoài | §3.12 | 3 | Bước 3/3 · Mật khẩu mới + nhập lại, thanh độ mạnh. |
| 12d | Quên mật khẩu – Thành công | [ForgotPasswordDone.png](screens/ForgotPasswordDone.png) | `html/ForgotPasswordDone.dc.html` | `forgot_password/done` | Ngoài | §3.12 | 3 | Thành công → Về Đăng nhập. |
| 13a | Đăng ký – Thông tin | [Register.png](screens/Register.png) | `html/Register.dc.html` | `register` | Ngoài | §3.13 | 3 | Thông tin đăng ký + đồng ý điều khoản (chưa tick thì khoá nút). |
| 13b | Đăng ký – Xác thực OTP | [RegisterOtp.png](screens/RegisterOtp.png) | `html/RegisterOtp.dc.html` | `register/otp` | Ngoài | §3.13 | 3 | Xác thực OTP số điện thoại → Dự án (trạng thái rỗng). |
| 18 | Thêm thiết bị bằng QR | [QRScan.png](screens/QRScan.png) | `html/QRScan.dc.html` | `qr_scan` | Tài khoản | §3.18 + ADR-004 | 3 | Khung quét QR camera, chỉ mở từ 14a. Đóng → 14a. |
| 2 | Dự án (chưa có dự án) | [ProjectsEmpty.png](screens/ProjectsEmpty.png) | `html/ProjectsEmpty.dc.html` | `projects (rỗng)` | Tài khoản · thẻ Dự án | §3.2, §3.34 | 1 | Trạng thái rỗng của màn Dự án — cùng route với Projects. |
| 2 | Dự án | [Projects.png](screens/Projects.png) | `html/Projects.dc.html` | `projects` | Tài khoản · thẻ Dự án | §3.2 | 1 | Danh sách dự án, tìm kiếm, nút + Thêm. Chạm thẻ → Màn hình chính. |
| 14a | Thêm dự án – Thiết bị | [AddProject.png](screens/AddProject.png) | `html/AddProject.dc.html` | `add_project/device` | Tài khoản | §3.14 | 3 | Bước 1/3 · Thiết bị: quét QR hoặc nhập serial + Kiểm tra; thẻ thông tin thiết bị hợp lệ. |
| 14b | Thêm dự án – Thông tin dự án | [AddProjectInfo.png](screens/AddProjectInfo.png) | `html/AddProjectInfo.dc.html` | `add_project/info` | Tài khoản | §3.14 | 3 | Bước 2/3 · Tên, loại công trình, địa chỉ + định vị, múi giờ, ngày lắp, kWp, ảnh. |
| 14c | Thêm dự án – Giá điện | [AddProjectTariff.png](screens/AddProjectTariff.png) | `html/AddProjectTariff.dc.html` | `add_project/tariff` | Tài khoản | §3.14 | 3 | Bước 3/3 · Giá điện (bậc thang EVN / cố định), giá bán lên lưới; Bỏ qua / Tạo dự án. |
| 3 | Tôi | [Me.png](screens/Me.png) · [language-open](screens/Me__language-open.png) | `html/Me.dc.html` | `me` | Tài khoản · thẻ Tôi | §3.3 + Thay đổi 0.6 §7 | 3 | Hồ sơ, thông báo, cài đặt hệ thống. Ngôn ngữ chọn bằng danh sách thả xuống tại đây. |
| 15 | Thông tin tài khoản | [Account.png](screens/Account.png) · [delete-dialog](screens/Account__delete-dialog.png) | `html/Account.dc.html` | `account` | Tài khoản | §3.15 + Thay đổi 0.6 §7 | 3 | Tên, email/SĐT (OTP), đổi mật khẩu, xoá tài khoản. Không có mục Ngôn ngữ. |
| 4 | Màn hình chính | [Main.png](screens/Main.png) | `html/Main.dc.html` | `project/{id}/home` | Dự án · thẻ Màn hình chính | §3.4 + ADR-003 | 1 | Nút ← về Dự án. Sơ đồ dòng năng lượng, box Chế độ hoạt động, box Pin, chọn Ngày/Tháng/Năm, 3 biểu đồ có thanh kéo. |
| 16 | Cấu hình dự án | [Config.png](screens/Config.png) · [role-A-chu-ho](screens/Config__role-A-chu-ho.png) | `html/Config.dc.html` | `project/{id}/config` | Dự án | §3.16 + Thay đổi 0.6 §1–2 + ADR-001, ADR-002 | 1 | Chế độ vận hành (5 chế độ, chọn 1), box Khung giờ sạc/xả, tham số theo vai trò A/B/C (tweak `role`), lịch sử thay đổi. |
| 16a | Lịch theo khung giờ – Danh sách chiến lược | [TouSchedule.png](screens/TouSchedule.png) | `html/TouSchedule.dc.html` | `project/{id}/tou` | Dự án | Thay đổi 0.6 §3 + ADR-002 | 1 | Danh sách chiến lược khung giờ + Thêm chiến lược. |
| 16b | Chi tiết chiến lược | [TouStrategy.png](screens/TouStrategy.png) · [edit-sheet](screens/TouStrategy__edit-sheet.png) · [add-sheet-full](screens/TouStrategy__add-sheet-full.png) · [weekend](screens/TouStrategy__weekend.png) | `html/TouStrategy.dc.html` | `project/{id}/tou/{strategyId}` | Dự án | Thay đổi 0.6 §4–5 + ADR-002 | 1 | Chi tiết chiến lược: thanh 24h, khung sạc/xả, ngày áp dụng, popup thêm/sửa/xoá khung. Tweak `strategy`. |
| 10 | Cảnh báo | [Alerts.png](screens/Alerts.png) · [filter-open](screens/Alerts__filter-open.png) | `html/Alerts.dc.html` | `project/{id}/alerts` | Dự án | §3.10 | 2 | Lọc Tất cả / Chưa xử lý / Đã xử lý. Chạm cảnh báo → 10a. |
| 10a | Chi tiết cảnh báo | [AlertDetail.png](screens/AlertDetail.png) · [temp](screens/AlertDetail__temp.png) · [firmware](screens/AlertDetail__firmware.png) · [pv](screens/AlertDetail__pv.png) | `html/AlertDetail.dc.html` | `project/{id}/alerts/{alertId}` | Dự án | §3.23 + Thay đổi 0.6 §6 | 2 | Chi tiết cảnh báo, biểu đồ 30 phút, hướng xử lý, Tạo yêu cầu hỗ trợ kỹ thuật. Tweak `alert`. |
| 11 | Nguồn năng lượng pin | [BatteryEnergy.png](screens/BatteryEnergy.png) | `html/BatteryEnergy.dc.html` | `project/{id}/battery_energy` | Dự án | §3.11 | 2 | Nguồn năng lượng đang có trong pin. |
| 5 | Thiết bị | [Device.png](screens/Device.png) | `html/Device.dc.html` | `project/{id}/devices` | Dự án · thẻ Thiết bị | §3.5 + ADR-003 | 2 | Nút ← về Dự án. Thẻ Inverter và Pin S16 Pre → dữ liệu chi tiết. |
| 17 | Theo dõi dữ liệu – Inverter | [DataInverter.png](screens/DataInverter.png) | `html/DataInverter.dc.html` | `project/{id}/data/inverter` | Dự án | §3.17 | 2 | Biểu đồ có thanh kéo (bước 15 phút), chú giải có giá trị. |
| 17 | Theo dõi dữ liệu – Pin S16 Pre | [DataBattery.png](screens/DataBattery.png) | `html/DataBattery.dc.html` | `project/{id}/data/battery` | Dự án | §3.17 | 2 | SOC, dòng BMS, cell cao/thấp nhất. |
| 6 | Dịch vụ | [Service.png](screens/Service.png) | `html/Service.dc.html` | `project/{id}/service` | Dự án · thẻ Dịch vụ | §3.6 + ADR-003 | 4 | Nút ← về Dự án. Box Hỗ trợ GG Power, tiện ích Khám phá, Hỗ trợ, Bảo hành. |
| 7 | Khám phá | [Explore.png](screens/Explore.png) | `html/Explore.dc.html` | `project/{id}/explore` | Dự án | §3.7 | 4 | 3 thẻ nổi Sản phẩm / Tin tức / Hướng dẫn. |
| 8 | Yêu cầu hỗ trợ | [Support.png](screens/Support.png) · [from-alert](screens/Support__from-alert.png) · [my-requests](screens/Support__my-requests.png) | `html/Support.dc.html` | `project/{id}/support` | Dự án | §3.8 + Thay đổi 0.6 §6 | 4 | 2 thẻ Gửi yêu cầu mới / Yêu cầu của tôi. Điền sẵn khi mở từ 10a (tweak `prefill`). |
| 8 | Chi tiết yêu cầu | [TicketDetail.png](screens/TicketDetail.png) | `html/TicketDetail.dc.html` | `project/{id}/support/{ticketId}` | Dự án | §3.8 | 4 | Chi tiết phiếu hỗ trợ. |
| 9 | Bảo hành | [Warranty.png](screens/Warranty.png) | `html/Warranty.dc.html` | `project/{id}/warranty` | Dự án | §3.9 | 4 | Thông tin, tình trạng, lịch sử bảo hành, Yêu cầu bảo hành. |

## Tweak trong prototype

| Màn | Tweak | Giá trị (mặc định trong ngoặc) |
|---|---|---|
| 16 Cấu hình dự án | `role` | A chủ hộ · B nhà phân phối · (C kỹ thuật GGP) |
| 16b Chi tiết chiến lược | `strategy` | (A Ngày thường) · B Cuối tuần |
| 10a Chi tiết cảnh báo | `alert` | (Mất lưới) · Nhiệt độ pin · Firmware · Điện áp PV |
| 8 Yêu cầu hỗ trợ | `prefill` | (Trống) · Từ cảnh báo |

Trong app, các giá trị này là tham số route hoặc vai trò tài khoản, không phải tuỳ chọn cho người dùng.

## Luồng nhiều bước

Quên mật khẩu (12a→12b→12c→12d), Đăng ký (13a→13b) và Thêm dự án (14a→14b→14c) là **các màn riêng** trong cùng một luồng. Dữ liệu nhập ở bước trước giữ trong một ViewModel dùng chung cho cả luồng (navigation graph lồng nhau), không mất khi quay lại bước trước.

## Đã bỏ

- **19 · Chọn ngôn ngữ** — chọn ngôn ngữ bằng danh sách thả xuống ở màn 3.
- **§3.30 Sửa khung sạc/xả** — thay bằng bottom sheet trong 16b.

## Màn trong đặc tả nhưng CHƯA có thiết kế

§3.20 – §3.35 (trừ §3.23 = 10a, §3.30 = popup 16b). Không dựng cho tới khi có thiết kế; nếu cần chỗ nối, tạo placeholder "Đang thiết kế".
