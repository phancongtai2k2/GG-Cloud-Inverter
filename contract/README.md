# Hợp đồng app ↔ backend

Nguồn sự thật chung cho `backend/` và `app/`. Bên nào đổi API thì sửa ở đây trước, rồi mới sửa code.

| File | Nội dung |
|---|---|
| [`openapi.yaml`](openapi.yaml) | Toàn bộ REST API `/api/v1`: đường dẫn, tham số, schema yêu cầu / phản hồi, mã lỗi |
| [`realtime.md`](realtime.md) | WebSocket `/ws/v1/realtime`: vé, frame hai chiều, giới hạn |
| [`CHANGES.md`](CHANGES.md) | Nhật ký thay đổi và danh sách việc đang chờ — **file bàn giao giữa hai bên** |

## Quy trình

1. Viết thay đổi vào `openapi.yaml` / `realtime.md` và thêm một mục vào `CHANGES.md` (có dòng "App cần làm").
2. Backend sửa code. `ContractTests` (trong `backend/tests`) đỏ nếu route thật lệch `openapi.yaml` hoặc có `$ref` hỏng.
3. App đọc mục mới trong `CHANGES.md` và cập nhật DTO / repository.

Xem nhanh: mở `openapi.yaml` bằng bất kỳ trình xem OpenAPI nào (Swagger Editor, Redocly, plugin IDE).
