# Mâu thuẫn đã biết trong đặc tả v0.6

Thân `dac-ta-chuc-nang-v0.6.md` còn một số đoạn chưa cập nhật theo mục **"Thay đổi trong phiên bản 0.6"** (viết tắt **TĐ**, "Thay đổi 0.6 §n"). Khi code: **theo ADR** trong `docs/adr/` rồi tới **TĐ**; điểm ghi "chưa chốt" xem `docs/OPEN_QUESTIONS.md`. Phòng EMS dùng danh sách này để sửa ở v0.7. Lập ngày 30/09/2026.

## Thân đặc tả trái với TĐ

| # | Vị trí trong thân v0.6 | Thân viết | TĐ viết | Xử lý |
|---|---|---|---|---|
| M1 | §2.1 Cây chức năng | "Màn hình Sửa khung sạc/xả và hộp xác nhận thay đổi — mới v0.4"; cây không có 16a/16b | §5: popup trong 16b thay màn 30; §3–4: 16a/16b | Theo TĐ |
| M2 | §2.1, §3.20 Điều hướng | Wi-Fi (20) là bước cuối của Thêm dự án: 14 → Tạo dự án → 20 → Màn hình chính | §9: 14c "Tạo dự án / Bỏ qua → Màn hình chính" | Theo TĐ; màn 20 chưa có thiết kế |
| M3 | §3.4 mục 4.3a | "Khi đang chạy lịch TOU hoặc chế độ **Dự phòng mất điện**…" | §1: đã bỏ Dự phòng mất điện | Theo TĐ |
| M4 | §3.8 Yêu cầu hỗ trợ | Quay lại luôn → Dịch vụ (6); không có chế độ điền sẵn từ cảnh báo | §6: mở từ 10a có dải "Tạo từ cảnh báo…", điền sẵn, quay lại → 10a | Theo TĐ |
| M5 | §3.12 Quên mật khẩu | "Luồng ba bước"; đổi xong "tự chuyển về Đăng nhập sau 2 giây" | §9: 4 màn 12a–12d; 12d có nút "Về Đăng nhập" (prototype cũng vậy) | Theo TĐ / ảnh |
| M6 | §3.15 bảng Thành phần | "Ngôn ngữ — Mở màn hình 19" | §7: màn 15 không có Ngôn ngữ; màn 19 đã bỏ | Theo TĐ |
| M7 | §3.19 (tiêu đề "đã bỏ") | Thân vẫn mô tả màn riêng, mở từ 3/15, "quay về màn hình trước" | §7: danh sách thả xuống trong màn 3, tự đóng | Theo TĐ; giữ các quy tắc không mâu thuẫn (lưu vào hồ sơ tài khoản; lần đầu lấy theo ngôn ngữ OS, ngoài 2 ngôn ngữ thì dùng Tiếng Việt) cho màn 3 |
| M8 | §3.16 mục 16.3 | SOC dự phòng: "thanh ngang **0–100 %** có vạch mốc 20 % và 50 %" (bảng cùng mục ghi 5–100 %) | §1: 5–100 %, bước 5 %, vùng chạm 44 px | Theo TĐ cho dải và bước; vạch dọc ở 50 % và nhãn 0 · 20 · 50 · 100 % **theo ảnh `Config.png`** (ADR-002 mục 11) |
| M9 | §3.16 mục 16.4, đoạn đầu | Lịch ở màn 16a *Lịch theo khung giờ*, mở bằng nút **Chỉnh sửa lịch** trên thẻ TOU | §1: không có nút; §2: box trên màn 16 mở 16a; §3: 16a = danh sách chiến lược | Theo TĐ |
| M10 | §3.16 mục 16.4 | "**Ba** khung sạc và **ba** khung xả" | §5 kiểm tra 1: tối đa **2** mỗi loại; §10: 2+2 hay 3+3 còn mở | **Đã chốt: 3+3** — ADR-002 mục 1 |
| M11 | §3.16 mục 16.4; §3.30 | Nguồn sạc chọn được lưới / máy phát (E04D) | §5: dòng cố định "Nguồn sạc: lưới điện" | Theo TĐ — không có control nguồn sạc |
| M12 | §3.16 mục 16.4; §3.30 | Chồng lấn: "tô đỏ đoạn giao nhau và khoá Lưu" | §5: hộp lỗi đỏ "Trùng với Xả 1 (…)", khoá Lưu, kiểm tra theo thứ tự 1–6 | Theo TĐ; **không** tô đỏ đoạn giao nhau (ADR-002 mục 7) |
| M13 | §3.16 mục 16.4 bảng | E225 "Ngày trong tuần áp dụng" do người dùng đặt trực tiếp (một mặt nạ) | §3, §8: nhiều chiến lược, mỗi thứ ≤ 1 chiến lược; backend nạp khung + E225 lúc 00:00 | Theo TĐ; app không ánh xạ chip ngày thẳng sang E225 |
| M14 | §3.16 mục 16.4a | "Màn **16a** không có công tắc…" (16a nay là danh sách); "Kích hoạt TOU: ghi toàn bộ lịch" không nói lịch của chiến lược nào | §3, §8 | Theo TĐ: ghi chiến lược áp dụng cho hôm nay |
| M15 | §3.16 mục 16.4a "Tạm ngưng theo ngày" | "**Không cho phép lưu** khi không có ngày nào được chọn" | §4: chỉ hiện dòng đỏ "Chưa chọn ngày nào nên lịch sẽ không chạy…"; §3: thứ chưa gán chạy như Tự tiêu thụ tối đa | **Đã chốt: chặn Lưu** khi không chọn ngày, giữ dòng đỏ — ADR-002 mục 3 |
| M16 | §3.23 | Nút "**Tạo phiếu hỗ trợ**"; thiếu Dự án, Thời lượng, chip trạng thái | §6: "**Tạo yêu cầu hỗ trợ kỹ thuật**"; bảng Thiết bị · Dự án · Bắt đầu · Kết thúc · Thời lượng | Theo TĐ |
| M17 | §3.30 (tiêu đề "thay bằng popup") | Thân vẫn tả bảng trượt mở từ 16a qua "Chỉnh sửa lịch", nguồn Lưới/Máy phát, ưu tiên "P2 — sau bản demo chỉ xem" | §5 | Theo TĐ. Phần **hộp xác nhận "cũ → mới"** và trạng thái Đang ghi… / Đã áp dụng / Hoàn về **vẫn hiệu lực** (xem §16.8, ADR-001) |

## Chỗ lỗi thời (không trái TĐ)

- §3.1: Quên mật khẩu, Đăng ký, Thêm thiết bị bằng QR ghi "(chưa có màn hình / chưa có luồng)" — đã có (12a–12d, 13a–13b, 18).
- §3.3: Thông tin tài khoản "(chưa có màn hình)" — đã có (15).
- §3.5: nút quét QR "(chưa có luồng)".
- §3.10: chưa nói chạm cảnh báo → 10a.
- §3.16 Điều hướng: "nút chế độ hoạt động" — theo §4.3a là box Chế độ hoạt động.

## Mâu thuẫn ngoài TĐ

- **C1.** §16.8 (gạch cuối), §8 hàng "Màn hình Cấu hình dự án ở bản demo", §3.30 ưu tiên "sau bản demo chỉ xem": demo chỉ xem, không gửi mã hàm 06/16. **Bị thay bởi ADR-001** (demo điều khiển thật có kiểm soát).
- **C2.** §16.4: "App gửi giờ và phút riêng, backend quy đổi; app không tự tính" — AGENTS.md §8 lại bắt buộc unit test mã hoá `giờ × 256 + phút` trong app. **Đã chốt:** backend mã hoá, app không có hàm này; bỏ khỏi AGENTS.md §8 (ADR-002 mục 17).
- **C3.** §5 Quy ước định dạng: thời điểm "hh:mm · dd/mm/yyyy"; AGENTS.md §5: `dd/MM HH:mm`. Cả hai dạng xuất hiện trên thiết kế. **Đã chốt:** helper cung cấp cả hai (`dd/MM HH:mm` và `HH:mm · dd/MM/yyyy`), mỗi màn dùng dạng như ảnh.
- **C4.** ADR-001 (bảng phạm vi) cho cả A/B/C ghi chế độ "Phát tối đa lên lưới" (E037 = 1); §16.5 nói chống phát ngược (E037 = 2 hoặc 3) là cấp B, chủ hộ không đổi; ADR đồng thời cấm ghi §16.5. **Đã chốt 01/10/2026:** chế độ này chỉ cấp B và C; xem "Sửa đổi 01/10/2026" trong ADR-001.
- **C5.** §16.2 ghi E037 = 1 cho "Phát tối đa" nhưng không nói khi rời thì ghi lại giá trị nào; E037 = 0 là off-grid (bảng SRNE). **Đã chốt:** khôi phục giá trị đã lưu, không bao giờ ghi 0.
- **C6.** ADR-001 liệt kê thanh ghi TOU E026–E032, E03B–E040, E047–E04C, thiếu E04D (nguồn sạc) và E225. **Đã chốt:** thêm cả hai (phụ lục ADR-001).
- **C7.** §16.2 không ghi E039/E20F khi vào TOU, nên ngoài khung giờ chạy theo ưu tiên PV cũ thay vì "Tự tiêu thụ tối đa" như TĐ §3. **Đã chốt:** vào TOU ghi E039 = 0, E20F = 0.
- **C8.** §16.4 cho E225 là mặt nạ 7 bit; PDF SRNE ghi dải 1–7 và mô tả "scheduled charging". **Chưa chốt, cần thử máy thật** (xem OPEN_QUESTIONS 01/10/2026).
- **C9.** §2.1, §3.1 (nút phụ "Thêm thiết bị bằng mã QR") và §3.18 ("gọi từ Đăng nhập và từ Thêm dự án"): quét QR khi chưa đăng nhập không gắn được thiết bị vào dự án. **Bị thay bởi ADR-004** (03/10/2026): bỏ nút ở Đăng nhập, màn 18 chỉ mở từ 14a.
