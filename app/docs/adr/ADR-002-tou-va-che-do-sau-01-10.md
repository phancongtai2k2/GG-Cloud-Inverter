# ADR-002 · Lịch TOU và chế độ vận hành: các quyết định ngày 01/10/2026

- Trạng thái: **Đã duyệt** · 01/10/2026 (Phòng EMS)
- Ghi đè: Thay đổi 0.6 §4, §5; ảnh và prototype `TouStrategy` (16b), `Config` (16) ở các điểm nêu dưới; đặc tả §16.3, §16.4, §16.4a.
- Liên quan: ADR-001 (kể cả "Sửa đổi 01/10/2026"); `docs/api/config-write.openapi.yaml`; `docs/design/BO_SUNG_v3.md` (giao diện cho các điểm chưa có thiết kế); `docs/spec/MAU_THUAN_v0.6.md` (M8, M10, M12, M15, C2–C7).

## Bối cảnh

Khi soạn hợp đồng API ghi cấu hình theo ADR-001, Phòng EMS đã chốt một loạt điểm làm thay đổi hành vi của màn 16, 16a, 16b. Các quyết định này trước đây nằm rải rác trong `OPEN_QUESTIONS.md` và `MAU_THUAN_v0.6.md`, xếp hạng thấp hơn ảnh PNG, nên agent sẽ làm theo prototype cũ. ADR này gom chúng lại để có hiệu lực hạng 1 (AGENTS.md §1).

Ảnh và prototype **chưa** được cập nhật theo ADR này. Khi ảnh khác ADR ở các điểm dưới đây thì theo ADR; mọi điểm khác vẫn theo ảnh.

## Quyết định

### Màn 16b · Chi tiết chiến lược

1. **Số khung tối đa: 3 sạc + 3 xả** (`MAX_SLOTS_PER_TYPE = 3`). Tên khung Sạc 1–3, Xả 1–3. Thông báo kiểm tra 1 đổi thành "Đã đủ 3 khung sạc. Hãy sửa hoặc xoá một khung có sẵn." (hoặc "…3 khung xả…").
2. **Sửa trên bản nháp, lưu một lần.** Mọi thao tác trong 16b (thêm / sửa / xoá khung, chip ngày, đổi tên) chỉ đổi bản nháp cục bộ. Thanh đáy cố định có nút **"Lưu thay đổi"**, chỉ bật khi có thay đổi. Bấm Lưu → hộp xác nhận liệt kê mọi thay đổi "cũ → mới" → **một** lệnh `tou_schedule` gửi toàn bộ danh sách chiến lược. Rời màn khi còn thay đổi chưa lưu thì hỏi bỏ thay đổi. Bottom sheet thêm/sửa khung giữ nguyên, nút "Lưu" trong sheet chỉ cập nhật bản nháp.
3. **Chặn Lưu khi chiến lược không có ngày nào.** Dòng đỏ của prototype giữ nguyên chữ ("Chưa chọn ngày nào nên lịch sẽ không chạy. Hãy chọn ít nhất một ngày."), đồng thời nút "Lưu thay đổi" bị khoá. Thay cho Thay đổi 0.6 §4 (chỉ cảnh báo).
4. **Chọn thứ đang thuộc chiến lược khác** vẫn chuyển thứ đó sang (Thay đổi 0.6 §4), nhưng chỉ trong bản nháp; chiến lược kia thay đổi theo và được liệt kê trong hộp xác nhận.
5. **Đổi tên chiến lược** trong 16b: tên 1–40 ký tự, không để trống. Giao diện theo `BO_SUNG_v3.md`.
6. **Giờ mặc định khi thêm khung:** dùng mặc định của Thay đổi 0.6 §5 (sạc 10:00–14:00, xả 06:00–08:00) nếu không trùng khung nào trong chiến lược. Nếu trùng, lấy khoảng trống đầu tiên đủ dài tính từ 00:00 (ứng viên bắt đầu: 00:00 và giờ kết thúc của từng khung, theo thứ tự) với cùng thời lượng mặc định (sạc 4 giờ, xả 2 giờ); không có thì thử 1 giờ; vẫn không có thì giữ mặc định §5 và để kiểm tra trùng báo lỗi. SOC và công suất giữ mặc định §5.
7. **Khung chồng lấn:** chỉ hộp lỗi đỏ và khoá Lưu như Thay đổi 0.6 §5; **không** tô đỏ đoạn giao nhau trên thanh 24h (MAU_THUAN M12).
8. **Xoá chiến lược** ("Xoá chiến lược này") là một thay đổi của bản nháp danh sách, lưu bằng cùng lệnh `tou_schedule`; hộp xác nhận ghi rõ các thứ của chiến lược bị xoá sẽ chạy như Tự tiêu thụ tối đa.

### Màn 16a · Danh sách chiến lược

9. **"Thêm chiến lược"** tạo chiến lược trống (chưa có ngày, chưa có khung) **chỉ trong bản nháp** và mở 16b; chưa gửi lệnh. Chưa lưu mà rời 16b thì chiến lược đó biến mất.

### Màn 16 · Cấu hình dự án

10. **"Phát tối đa lên lưới" chỉ cấp B và C** (ADR-001 sửa đổi). Với cấp A **ẩn hẳn** thẻ này — trừ khi inverter đang chạy chế độ này: khi đó vẫn hiện thẻ (đang chọn, bị khoá) để không hiển thị sai chế độ. App dựa vào `write_access.writable_modes`, không tự suy từ vai trò.
11. **Thanh SOC dự phòng theo ảnh `Config.png`:** dải 5–100 %, bước 5 %; vạch dọc ở 50 %; nhãn dưới thanh "0 · 20 · 50 · 100 %" (MAU_THUAN M8). *Đính chính 01/10/2026: bản đầu ghi nhầm là không có vạch mốc.*
12. **Thiếu truyền thông BMS:** thanh SOC dự phòng bị vô hiệu kèm chú thích (`blocked_groups` có `BMS_NOT_CONNECTED`).
13. **Đang ở GG AI** vẫn chuyển được sang các chế độ trong `writable_modes`; SOC và lịch TOU vẫn sửa được.
14. **Sửa lịch TOU khi chế độ hiện tại không phải TOU:** lệnh chỉ lưu ở backend (`outcome.device_written = false`); app báo lịch sẽ chạy khi chọn chế độ Theo khung giờ.

### Ghi cấu hình (chung cho 16, 16a, 16b)

15. Mọi lệnh ghi theo `docs/api/config-write.openapi.yaml`: gửi kèm `based_on_version` và `Idempotency-Key`; hỏi lại trạng thái theo `poll_after_ms` tới trạng thái cuối; UI chỉ đổi khi `applied`. `CONFIG_STALE` → đọc lại cấu hình rồi cho người dùng xác nhận lại. `device_state = unknown` → đọc lại cấu hình và báo người dùng. `MODBUS_EXCEPTION` → một thông báo chung.
16. Backend chỉ trả `code`; mọi chữ hiển thị nằm trong `strings.xml` theo `BO_SUNG_v3.md`.

### Mã hoá giờ thanh ghi

17. App gửi giờ và phút riêng; **backend** quy đổi `giờ × 256 + phút`. App không có hàm mã hoá này; bỏ khỏi danh sách test bắt buộc trong AGENTS.md §8 (MAU_THUAN C2).

## Hệ quả

- Domain cần thêm: bản nháp danh sách chiến lược và phép so sánh nháp ↔ bản đã lưu (sinh danh sách "cũ → mới"), gợi ý giờ mặc định, kiểm tra ≥ 1 ngày. Có unit test (AGENTS.md §8).
- Việc cho thiết kế: cập nhật prototype 16b (3+3, thanh Lưu, đổi tên, chặn Lưu) và 16 (ẩn thẻ cấp A) theo ADR này và `BO_SUNG_v3.md`.
- Việc cho đặc tả v0.7: sửa Thay đổi 0.6 §4, §5; §16.3, §16.4, §16.4a.
