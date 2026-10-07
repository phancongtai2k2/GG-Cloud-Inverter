# ADR-003 · Nút quay lại về Dự án ở các màn cấp dự án

- Trạng thái: **Đã duyệt** · 02/10/2026 (chủ sản phẩm, khi kiểm tra app trên máy ảo)
- Ghi đè: AGENTS.md §4 ("Màn con không có thanh thẻ và có nút quay lại" — trước đây hiểu là màn có thanh thẻ thì không có nút quay lại); ảnh `Main.png`, `Device.png`, `Service.png` và HTML tương ứng (đầu trang không có nút ←).
- Liên quan: đặc tả §2 (hai cấp điều hướng), mục 8 "Chuyển nhanh giữa các dự án: quay lại danh sách Dự án"; `docs/design/navigation.md`; `docs/design/BO_SUNG_v3.md` §10.

## Bối cảnh

Ba màn cấp dự án (4 · Màn hình chính, 5 · Thiết bị, 6 · Dịch vụ) có thanh thẻ nổi và theo thiết kế không có nút quay lại. Cách duy nhất để về danh sách Dự án (2) là nút back của hệ thống — người dùng không thấy được, và đặc tả mục 8 đã chốt "quay lại danh sách Dự án" là cách chuyển giữa các dự án.

## Quyết định

1. Đầu trang của **Màn hình chính, Thiết bị và Dịch vụ** có nút **←** bên trái tiêu đề, cùng kiểu màn con: vùng chạm 48 dp, mũi tên 24 dp, đệm trái 4 dp; tiêu đề giữ 22/28 600.
2. Bấm ← **về danh sách Dự án (2)** và bỏ các màn của dự án khỏi back stack (không quay về thẻ trước). Nếu Dự án không có trong back stack thì mở Dự án làm màn gốc.
3. TalkBack đọc **"Quay lại Dự án"**.
4. Nút back của hệ thống giữ nguyên: ở Thiết bị / Dịch vụ về Màn hình chính của dự án, ở Màn hình chính về Dự án.
5. Màn cấp tài khoản (2 · Dự án, 3 · Tôi) **không** có nút quay lại (là màn gốc).

## Hệ quả

- `ScreenHeader` nhận nhãn TalkBack riêng cho nút quay lại; `AppNavGraph` có hàm `backToProjects()` dùng chung cho 3 màn.
- Thiết kế cần cập nhật `Main.png`, `Device.png`, `Service.png` (và HTML) theo mục 1.
- Đặc tả v0.7 bổ sung vào §2 / §3.4–3.6.
