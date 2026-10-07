# Thay đổi thiết kế sau đặc tả v0.5

File này **ưu tiên hơn** `dac-ta-chuc-nang-v0.5.md` ở các điểm dưới đây. Ảnh và HTML trong `docs/design/` đã phản ánh các thay đổi này. Cập nhật lần cuối: 30/09/2026 (bản v3).

## 1. Chế độ vận hành (màn 16)

- **Bỏ chế độ "Dự phòng mất điện".** Còn 5 chế độ: GG AI (pha 2, đang khoá), Tự tiêu thụ tối đa, Theo khung giờ (TOU), Phát tối đa lên lưới, Ưu tiên sạc pin. Chỉ chọn 1 chế độ tại một thời điểm.
- Thanh trượt **SOC dự phòng (cắt xả, E00F)** giữ nguyên: 5–100 %, bước 5 %, dưới thanh có số giờ dự phòng ước tính.
- Thẻ chế độ TOU **không** có nút "Chỉnh sửa lịch".

## 2. Box "Khung giờ sạc/xả" trên màn 16

- Mục riêng, luôn hiển thị, giữa "Chế độ vận hành" và "Pin và ngưỡng SOC". Chạm cả box → 16a.
- Nội dung: dòng nhỏ "Hôm nay" + tên chiến lược áp dụng hôm nay (không có ô chữ, không có ngày/thứ, không có nhãn trạng thái); thanh 24h (mốc 00–06–12–18–24); tóm tắt "N giai đoạn: a lần sạc, b lần xả" và từng giai đoạn theo giờ bắt đầu (loại, `HH:mm – HH:mm`, thời lượng, "qua đêm").
- Không có lịch tuần.

## 3. Màn 16a · Danh sách chiến lược

**Chiến lược** = một bộ khung sạc/xả + tập thứ áp dụng. Tối đa 4 chiến lược. **Mỗi thứ chỉ thuộc tối đa một chiến lược**; thứ chưa gán chạy như Tự tiêu thụ tối đa.

- Đoạn mô tả ngắn; mỗi chiến lược một thẻ: tên, các thứ áp dụng (viết tắt T2…CN, hoặc "Hằng ngày"), nhãn "Hôm nay" nếu áp dụng hôm nay, thanh 24h thu nhỏ, dòng tóm tắt "4 giai đoạn: 2 lần sạc, 2 lần xả · 09:00–12:00, …".
- **Không** có ô chữ A/B trên thẻ, **không** có khối lịch tuần.
- Nút "Thêm chiến lược" (ẩn khi đủ 4) → tạo chiến lược trống và mở 16b.
- Dữ liệu mẫu (hôm nay T5 24/09/2026):

| Chiến lược | Thứ | Sạc | Xả |
|---|---|---|---|
| Ngày thường | T2–T6 | 22:00–06:00 (SOC 90 %, 3.000 W), 13:00–16:00 (100 %, 3.000 W) | 09:00–12:00 (50 %, 4.000 W), 18:00–21:00 (20 %, 5.000 W) |
| Cuối tuần | T7, CN | 00:00–06:00 (90 %, 3.000 W), 14:00–16:00 (100 %, 3.000 W) | 10:00–12:00 (60 %, 4.000 W), 18:00–23:00 (20 %, 5.000 W) |

## 4. Màn 16b · Chi tiết chiến lược

- Tiêu đề = tên chiến lược; quay lại → 16a.
- Thanh 24h + chú giải (Sạc cam `#e0700f`, Xả xanh `#149c4a`, Tự động xám). **Không** có box "Tóm tắt lịch".
- **Danh sách khung chỉ gồm các khung đã đặt** (không có dòng "Chưa đặt"). Tên khung đánh số liên tục theo loại: Sạc 1, Sạc 2…, Xả 1, Xả 2…
- **Xoá khung → khung biến mất**; các khung cùng loại phía sau dồn lên và đánh số lại. Không còn khung nào → dòng "Chưa có khung sạc hoặc xả nào. Hệ thống chạy như Tự tiêu thụ tối đa cả ngày."
- "Áp dụng các ngày": 7 chip. Chọn: nền `primary`, chữ trắng. Không chọn: nền `#eef1ec`, chữ `#9aa59f`, mờ 60 %; **CN chữ đỏ `#c62828`**. Chọn thứ đang thuộc chiến lược khác → chuyển thứ đó sang, kèm dòng nhắc `T7, CN đang thuộc "Cuối tuần". Chọn các ngày này sẽ chuyển chúng sang chiến lược đang sửa.` Không chọn ngày nào → dòng đỏ "Chưa chọn ngày nào nên lịch sẽ không chạy…".
- Nút "Thêm khung sạc hoặc xả" (viền đứt), danh sách quy tắc, nút đỏ "Xoá chiến lược này" (khi có > 1 chiến lược).
- Còn thiếu: đổi tên chiến lược (xem OPEN_QUESTIONS).

## 5. Popup thêm / sửa khung (bottom sheet trong 16b, thay §3.30)

- **Thêm:** tiêu đề "Thêm khung giờ", segmented "Khung sạc / Khung xả". Mặc định sạc 10:00–14:00, SOC 80 %, 3.000 W; xả 06:00–08:00, SOC 40 %, 4.000 W.
- **Sửa:** tiêu đề "Sửa khung Sạc 1" + vạch màu, điền sẵn; nút đỏ "Xoá khung này". Khi sửa, các khung khác trên thanh 24h mờ 35 %.
- Trường: Giờ bắt đầu, Giờ kết thúc (hh:mm), SOC dừng (%), Công suất tối đa (W); khung sạc có dòng "Nguồn sạc: lưới điện".
- **Kiểm tra** (hộp lỗi đỏ, khoá nút Lưu), theo thứ tự:
  1. Đủ khung cùng loại → "Đã đủ 2 khung sạc. Hãy sửa hoặc xoá một khung có sẵn."
  2. Sai định dạng giờ → "Giờ phải có dạng hh:mm, ví dụ 01:30."
  3. Bắt đầu = kết thúc → "Giờ kết thúc phải khác giờ bắt đầu."
  4. SOC ngoài 0–100 → "SOC dừng phải trong khoảng 0–100 %."
  5. Công suất ngoài 0–12.000 W → "Công suất tối đa phải trong khoảng 0–12.000 W."
  6. Trùng khung khác, **tính cả khung qua nửa đêm**; chạm mép (05:00 và 05:00) không tính là trùng → "Trùng với Xả 1 (09:00 – 12:00). Các khung không được chồng lấn."
- Lưu: công suất định dạng lại có dấu chấm nghìn (3500 → 3.500).

## 6. Cảnh báo và yêu cầu hỗ trợ

- **Màn 10:** chạm một cảnh báo → **10a · Chi tiết cảnh báo** (thực hiện §3.23 của đặc tả).
- **10a gồm:** icon + chip mức độ (Lỗi/Cảnh báo/Thông tin) + chip trạng thái (Chưa xử lý/Đã xử lý), tiêu đề, mô tả; bảng Thiết bị · Dự án · Bắt đầu · Kết thúc ("Đang diễn ra" chữ đỏ / "Tự phục hồi lúc …") · Thời lượng; **Mã lỗi** (mã thô + tên tiếng Việt, vị trí bit để `[MÃ BIT]`); **Diễn biến 30 phút** (biểu đồ nhỏ, vạch đứt tại thời điểm cảnh báo; cảnh báo thông tin không có biểu đồ); **Nguyên nhân có thể**; **Hướng xử lý** (bước đánh số); nút chính **"Tạo yêu cầu hỗ trợ kỹ thuật"** → màn 8; nút phụ **"Đánh dấu đã xử lý"** (bật/tắt).
- **Màn 8 mở từ 10a:** dải đỏ "Tạo từ cảnh báo · <tiêu đề> · <thời điểm>", "Serial, firmware và mã lỗi sẽ được đính kèm tự động."; loại = Kỹ thuật, dự án = dự án của cảnh báo, tiêu đề = "<tiêu đề cảnh báo> – <tên dự án>", mô tả điền sẵn mức độ, thời điểm, trạng thái, mã lỗi. Quay lại → 10a.

## 7. Ngôn ngữ

- **Bỏ màn 19 · Chọn ngôn ngữ.**
- Chọn ngôn ngữ ở **màn 3 · Tôi**, mục Cài đặt hệ thống: dòng "Ngôn ngữ" mở danh sách thả xuống ngay bên dưới, 2 lựa chọn **Tiếng Việt / English** (chỉ tên, không có dòng mô tả); lựa chọn hiện tại nền xanh nhạt + dấu ✓; chọn xong tự đóng và áp dụng ngay.
- **Màn 15 · Thông tin tài khoản không có mục Ngôn ngữ.**

## 8. Ràng buộc phần cứng cần xử lý ở backend / EMS

- Inverter SRNE chỉ có **một bộ khung TOU** (E026–E04D) và một mặt nạ ngày E225 — không lưu được nhiều chiến lược.
- Backend **nạp khung của chiến lược áp dụng cho ngày mới xuống inverter lúc 00:00** mỗi khi ngày mới thuộc chiến lược khác ngày trước. E02C/E033 tự bật/tắt theo §16.4a.
- **Chưa chốt:** xử lý khi gateway mất kết nối lúc chuyển ngày. App chỉ hiển thị, không tự ghi.

## 9. Tách luồng nhiều bước thành màn riêng

| Luồng | Màn | Điều hướng |
|---|---|---|
| Quên mật khẩu | 12a Tìm tài khoản · 12b Mã xác thực · 12c Mật khẩu mới · 12d Thành công | Gửi mã xác thực → Xác nhận → Đổi mật khẩu → Về Đăng nhập. Quay lại: 12b→12a, 12c→12b, 12a/12d→Đăng nhập |
| Đăng ký | 13a Thông tin · 13b Xác thực OTP | Tạo tài khoản (khoá khi chưa đồng ý điều khoản) → Xác thực → Dự án (rỗng). Quay lại 13b→13a |
| Thêm dự án | 14a Thiết bị · 14b Thông tin dự án · 14c Giá điện | Tiếp tục (khoá khi chưa kiểm tra serial) → Tiếp tục → Tạo dự án / Bỏ qua → Màn hình chính. Quay lại 14b→14a, 14c→14b |

- Mỗi màn giữ thanh tiến trình ở đầu, tô đến bước hiện tại.
- Dữ liệu nhập giữ trong ViewModel dùng chung cho cả luồng (nested navigation graph).
