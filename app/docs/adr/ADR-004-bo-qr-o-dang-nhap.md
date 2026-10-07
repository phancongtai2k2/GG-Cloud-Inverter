# ADR-004 · Bỏ nút "Thêm thiết bị bằng mã QR" ở màn Đăng nhập

- Trạng thái: **Đã duyệt** · 03/10/2026 (chủ sản phẩm, khi chuẩn bị đợt 3)
- Ghi đè: đặc tả §3.1 (nút phụ "Thêm thiết bị bằng mã QR"), §2.1 và §3.18 ("gọi từ Đăng nhập và từ Thêm dự án"); ảnh `Login.png` và `Login.dc.html` (dải "hoặc" và nút QR dưới nút Đăng nhập).
- Liên quan: đặc tả §3.14 bước 1, §3.18; `docs/design/navigation.md`; `docs/design/screens.md`.

## Bối cảnh

Màn 18 · Quét QR mở được từ hai nơi: Đăng nhập (1) và bước 1 của Thêm dự án (14a). Nếu quét từ Đăng nhập, người dùng chưa đăng nhập nên không thể gắn thiết bị vào dự án. Muốn gắn được thì phải giữ serial qua bước đăng nhập / đăng ký, và luồng đó chưa được thiết kế.

## Quyết định

1. Màn Đăng nhập **không có** dải "hoặc" và nút "Thêm thiết bị bằng mã QR". Các phần khác của màn giữ nguyên.
2. Màn 18 chỉ mở từ **14a · Thêm dự án – Thiết bị** (nút "Quét mã QR"), tức là chỉ mở khi đã đăng nhập. Đóng màn 18 thì quay về 14a; "Dùng thiết bị này" điền serial vào 14a.
3. Nút QR ở đầu trang màn 5 · Thiết bị giữ nguyên chỗ nối. Đích thật của nút này là màn 27 · Thêm thiết bị vào dự án (đặc tả §3.27, chưa có thiết kế).

## Hệ quả

- Code: bỏ nút và chuỗi `login_or`, `login_add_device_qr` khỏi màn 1; route `QrScan` thuộc cấp tài khoản.
- `navigation.md`, `screens.md`: bỏ đường 1 → 18; màn 18 thuộc cấp Tài khoản.
- Thiết kế cần cập nhật `Login.png` và `Login.dc.html` theo mục 1.
- Đặc tả v0.7 sửa §2.1, §3.1, §3.18 theo ADR này.
