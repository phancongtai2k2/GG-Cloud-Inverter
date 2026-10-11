# Hợp đồng API

Từ 11/10/2026 hợp đồng app ↔ backend nằm ở thư mục gốc của kho: [`../../../contract/`](../../../contract/README.md)
(`openapi.yaml`, `realtime.md`, `CHANGES.md`). Bản `live` đã nối backend thật; mọi endpoint dùng trong
`data/remote/` phải có trong `contract/openapi.yaml`.

| File | Trạng thái |
|---|---|
| `config-write.openapi.yaml` | **Đã thay thế** bởi `contract/openapi.yaml` (nhóm `config`). Giữ lại để tra lịch sử quyết định (ADR-001, mô tả thanh ghi); không dùng làm nguồn. |

Quy tắc cho agent:
- Cần endpoint hoặc trường chưa có trong `contract/openapi.yaml`: không tự đặt. Ghi vào mục "Đang chờ" của
  `contract/CHANGES.md` (và `docs/OPEN_QUESTIONS.md` nếu cần quyết định), dùng `Fake…Repository` cho tới khi backend làm xong.
- Backend đổi hợp đồng: đọc mục mới nhất trong `contract/CHANGES.md`, làm theo dòng "App cần làm".
