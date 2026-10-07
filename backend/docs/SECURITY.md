# Bảo mật

## Đã có trong mã nguồn

| Mối nguy | Cách xử lý | Nơi |
|---|---|---|
| Lộ mật khẩu khi lộ DB | Argon2id (19 MiB, 2 vòng), muối riêng từng tài khoản | `Security.cs` |
| Dò mật khẩu | Khoá 15 phút sau 5 lần sai; giới hạn 20 lần gọi/phút/địa chỉ cho nhóm `/auth`; thời gian trả lời như nhau khi tài khoản không tồn tại | `AuthService.cs`, `Program.cs` |
| Lộ access token | Hết hạn sau 15 phút; phiên bị đăng xuất/thu hồi có hiệu lực trong 20 giây | `Program.cs` (`OnTokenValidated`) |
| Lộ refresh token | Chỉ lưu SHA-256; xoay vòng mỗi lần dùng; dùng lại token cũ thì thu hồi cả phiên | `AuthService.RefreshAsync` |
| Dò mã OTP | Mã 6 số, lưu dạng băm, hết hạn 5 phút, tối đa 5 lần sai, gửi lại cách nhau 60 giây, 5 mã/giờ | `AuthService.cs` |
| Dò tài khoản tồn tại | "Quên mật khẩu" và "gửi lại mã" trả lời giống nhau cho mọi tài khoản | `AuthService.cs` |
| Đọc/sửa dữ liệu dự án người khác | Mọi endpoint lấy dự án/thiết bị qua `AccessService` (kiểm tra thành viên); không thuộc thì trả 404 | `Common.cs` |
| Ghi nhầm xuống biến tần | Thiết bị mặc định chỉ xem (danh sách cho phép); công tắc tắt ghi toàn cục; vai trò chủ hộ không chọn được chế độ phát lên lưới; 1 lệnh/5 giây/thiết bị; chỉ báo "đã áp dụng" sau khi đọc lại; nhật ký 90 ngày | `Commands.cs` |
| Token trong URL của WebSocket | Vé dùng một lần, sống 30 giây | `DataEndpoints.cs` (`RealtimeTickets`) |
| Tệp tải lên độc hại | Giới hạn cỡ; nhận diện loại bằng nội dung (JPEG/PNG/WebP/MP4); tên tệp do máy chủ đặt; lưu ngoài thư mục web; tải về chỉ cho chủ phiếu | `Common.cs` (`FileStore`) |
| SQL injection | Toàn bộ truy vấn dùng tham số; tên bảng/cột động chỉ lấy từ hằng trong mã | toàn bộ |
| Lộ thông tin qua lỗi | Lỗi chỉ trả `{"code": ...}`; chi tiết nằm ở log máy chủ; mật khẩu/OTP/token không ghi log (trừ OTP khi bật `LogOtpCodes` để phát triển) | `Program.cs` |
| Bí mật trong repo | Chuỗi kết nối, khoá JWT, tài khoản MQTT đọc từ biến môi trường `GGP_*`; repo chỉ có `.env.example` | `run.ps1` |
| Phía app | Refresh token mã hoá bằng khoá AES-GCM trong Android Keystore; bản release chỉ HTTPS (HTTP chỉ mở cho `10.0.2.2` ở bản debug) | repo app, `data/remote/ApiClient.kt` |

## Phải làm trước khi đưa ra Internet

1. **HTTPS.** Đặt backend sau reverse proxy có chứng chỉ (Caddy/Nginx), khai báo địa chỉ proxy ở `Proxy:Known` để
   giới hạn tần suất tính đúng địa chỉ người dùng. Hiện backend chỉ chạy HTTP trong mạng nội bộ.
2. **Tài khoản MQTT riêng cho từng thiết bị.** Máy chủ đã có broker riêng với phân quyền theo thiết bị (xem README,
   mục Broker MQTT). Còn lại: chuyển từng datalogger sang broker này bằng tài khoản riêng của nó, và ngừng dùng tài
   khoản chung trên broker cũ. Thiết bị nào còn dùng tài khoản chung thì chưa được bảo vệ.
3. **Mã nhận thiết bị.** Hiện chỉ cần biết serial của một thiết bị chưa thuộc dự án nào là thêm được vào tài khoản.
   Cần mã bí mật in trên tem (trong mã QR) do backend kiểm tra.
4. **Tắt `LogOtpCodes`** và nối nhà cung cấp SMS/e-mail thật (`IOtpSender`).
5. **Bỏ tenant `dev`** khỏi `MQTT_TENANTS` trên máy chủ thật (bộ giả lập có thể tạo thiết bị giả).
6. Sao lưu PostgreSQL và xoay khoá JWT theo lịch.

## Chưa kiểm chứng

Chưa có đánh giá bảo mật độc lập hay kiểm thử xâm nhập. Các mục ở bảng trên được kiểm bằng `tools/smoke_test.py`
và `tools/command_test.py` (xoay token, dùng lại token, phân quyền chéo tài khoản, vé WebSocket, tệp tải lên,
quyền admin); phần giới hạn tần suất và khoá tài khoản chưa có test riêng.
