# Hợp đồng API

| File | Phạm vi | Trạng thái |
|---|---|---|
| `config-write.openapi.yaml` | Đọc cấu hình, gửi lệnh ghi (3 nhóm của ADR-001), lịch sử thay đổi, công tắc tắt ghi, danh sách serial demo | **Nháp 0.2.0 — chưa duyệt** (0.2.0: thêm tham số chỉ xem `settings`, tên người thực hiện ở lịch sử) |

Mọi phần khác (đăng nhập, dự án, realtime, cảnh báo, hỗ trợ…) **chưa có hợp đồng**.

Quy tắc cho agent:
- Chỗ nào chưa có hợp đồng: chỉ dùng `Fake…Repository`, không tự đặt endpoint.
- `config-write.openapi.yaml` còn là nháp. Dùng nó để định hình model và `Fake…Repository` của màn 16, 16a, 16b (trạng thái lệnh, mã lỗi), **chưa nối backend thật** cho tới khi Phòng EMS và backend duyệt và đổi trạng thái thành "Đã duyệt".
- Điểm chưa rõ: ghi vào `docs/OPEN_QUESTIONS.md`.
