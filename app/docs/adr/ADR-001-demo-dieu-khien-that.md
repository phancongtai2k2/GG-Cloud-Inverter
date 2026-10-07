# ADR-001 · Bản demo có điều khiển thật qua backend, có kiểm soát

- Trạng thái: **Đã duyệt** · Đề xuất 30/09/2026 · Duyệt 01/10/2026 (Phòng EMS)
- Thay thế: đặc tả v0.6 §16.8 (gạch "Đã chốt cho bản demo…"), mục 8 hàng "Màn hình Cấu hình dự án ở bản demo", ưu tiên ở §3.30.
- Sửa đổi: 01/10/2026 (Phòng EMS) — xem mục "Sửa đổi 01/10/2026" ở cuối file; mục này ưu tiên hơn bảng phạm vi ở trên.
- Liên quan: `AGENTS.md` §7; đặc tả §16.1, §16.4a, §16.8; Thay đổi 0.6 §8; `docs/spec/MAU_THUAN_v0.6.md` (C1).

## Bối cảnh

Đặc tả v0.6 chốt bản demo 12/10/2026 ở chế độ chỉ xem: đọc giá trị thật (mã hàm 03), không gửi mã hàm 06/16. Team quyết định demo phải cho thấy điều khiển thật để kiểm chứng toàn bộ đường ghi app → backend → gateway → inverter. Các nguyên tắc an toàn vẫn giữ: app không ghi trực tiếp xuống inverter; UI chỉ cập nhật khi backend báo ghi thành công.

## Quyết định

Bản demo cho phép ghi cấu hình thật, **chỉ qua backend EMS**, trong phạm vi và điều kiện dưới đây. `BuildConfig.CONFIG_READ_ONLY = false` cho bản demo; cờ này giữ vai trò công tắc chỉ xem khẩn cấp.

### Phạm vi được ghi (danh sách cho phép)

| Nhóm | Tham số | Thanh ghi | Vai trò |
|---|---|---|---|
| Chế độ vận hành | 4 chế độ (trừ GG AI đang khoá) | E039, E20F, E037 (chỉ theo 16.2); E02C/E033 tự ghi theo 16.4a | A, B, C |
| SOC dự phòng | 5–100 %, bước 5 % | E00F | A, B, C |
| Lịch TOU | Khung của chiến lược áp dụng hôm nay; E225 | E026–E032, E03B–E040, E047–E04C | A, B, C |

**Không được ghi trong demo** — control hiện giá trị nhưng bị vô hiệu, chú thích "Bản demo — chưa mở": nhóm cấp B (§16.3 dòng sạc, §16.5 lưới điện), §16.6 bảo vệ, DF00 bật/tắt inverter, §16.7 BMS / Modbus / song song, điện áp và tần số đầu ra.

Cấp C cũng chỉ được ghi trong danh sách cho phép ở trên, không mở thêm nhóm nào cho bản demo.

### Kiểm soát bắt buộc

1. **Chỉ thiết bị demo:** backend chỉ nhận lệnh ghi cho các serial trong danh sách cho phép do Phòng EMS quản lý; thiết bị khác bị từ chối, app hiện "Thiết bị này chỉ xem".
2. **Công tắc tắt khẩn cấp phía server:** backend có cờ tắt ghi toàn cục; khi tắt, app hiển thị như chỉ xem mà không cần phát hành bản mới.
3. **Phân quyền theo vai trò** (§16.1): backend kiểm tra vai trò ở mỗi lệnh ghi; app chỉ ẩn/hiện theo vai trò backend cấp.
4. **Hộp xác nhận** "tham số: giá trị cũ → giá trị mới" cho mọi thay đổi (§16.8); không lưu ngầm.
5. **Đọc lại để xác nhận:** mã hàm 03 sau 3 giây; lệch thì hoàn về giá trị cũ và báo lỗi. UI chỉ đổi khi backend báo thành công.
6. **Khoá ghi khi gateway mất kết nối > 60 giây**, hiện dải "Mất kết nối với hệ thống — không thể thay đổi cấu hình" (§16.8).
7. **Nhật ký thay đổi:** người thực hiện, tham số, giá trị cũ, giá trị mới, thời điểm, kết quả đọc lại; giữ 90 ngày.
8. **Thứ tự ghi TOU** theo §16.4a: ghi toàn bộ lịch → đọc lại → mới bật E02C/E033.
9. **Giới hạn tần suất:** tối đa 1 lệnh ghi / 5 giây / thiết bị, backend kiểm tra.
10. **Có người trực:** trong buổi demo có kỹ thuật Phòng EMS (tại chỗ hoặc từ xa) nắm công tắc tắt khẩn cấp.

## Hệ quả

- App: màn 16, 16a, 16b không còn trạng thái "chỉ xem toàn màn"; thêm trạng thái Đang ghi… / Đã áp dụng / Hoàn về và các lỗi "không có quyền", "thiết bị chỉ xem", "mất kết nối".
- **Cần hợp đồng API ghi cấu hình trong `docs/api/`** trước khi nối backend. Chưa có thì dùng `Fake…Repository` mô phỏng các kết quả thành công / hoàn về / bị từ chối. **Không tự đặt endpoint.**
- Unit test: danh sách cho phép (tham số ngoài danh sách → control bị vô hiệu); chuyển trạng thái của một lệnh ghi.
- Đặc tả v0.7 cần sửa §16.8, mục 8 và §3.30 theo ADR này.

## Phương án đã cân nhắc

- **Giữ chỉ xem như v0.6** — an toàn nhất nhưng không kiểm chứng được đường ghi. Loại.
- **Cho ghi toàn bộ theo vai trò** — phạm vi rủi ro lớn (lưới, bảo vệ, DF00) khi chưa kiểm thử. Loại.

## Sửa đổi 01/10/2026

Phát hiện khi đối chiếu với bảng SRNE V2.11 và đặc tả §16.2, §16.4, §16.5. Phòng EMS đã quyết:

1. **Thêm hai thanh ghi vào nhóm Lịch TOU:** E04D (nguồn sạc; mặc định 0 là không sạc từ lưới nên khung sạc đêm sẽ không chạy nếu không ghi) và E225 (ngày áp dụng; bảng gốc chỉ nhắc E225 trong chữ, chưa có trong dải thanh ghi). E04D chỉ bật bit sạc từ lưới, không bật bit máy phát.
2. **Chế độ "Phát tối đa lên lưới" (E039 = 2, E037 = 1) chỉ cấp B và C**, theo §16.5 (chống phát ngược là cấp B; chủ hộ không đổi). Cấp A không chọn được. Bảng phạm vi ở trên ghi "A, B, C" cho nhóm Chế độ vận hành là chưa đúng riêng chế độ này.
3. **E037 mang giá trị 0–4** (0 off-grid, 1 nối lưới, 2 và 3 chống phát ngược, 4 ghép AC), không phải công tắc. Backend lưu giá trị E037 đọc được trước khi vào chế độ Phát tối đa và khôi phục đúng giá trị đó khi rời. **Không bao giờ ghi E037 = 0.**
4. **Vào TOU ghi thêm E039 = 0 và E20F = 0** (cả hai đã có trong danh sách), thứ tự: E039/E20F → lịch → đọc lại → E02C/E033. Ngày không thuộc chiến lược nào: lúc 00:00 backend ghi E02C = E033 = 0.
5. **Sửa lịch TOU khi chế độ hiện tại không phải TOU:** chỉ lưu ở backend, nạp xuống khi vào TOU.
6. **Cần thử trên máy thật trước ngày 12/10:** E225 trong PDF có dải 1–7 (có nhận bit Thứ Năm–CN không?) và mô tả là "scheduled charging" (có áp cho khung xả không?).
