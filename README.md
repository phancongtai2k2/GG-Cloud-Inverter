# GG Cloud Inverter

Phần cloud của hệ thống giám sát và điều khiển biến tần GG Power: backend nhận dữ liệu từ datalogger và
ứng dụng Android cho người dùng.

| Thư mục | Nội dung |
|---|---|
| [`backend/`](backend/) | Backend .NET 9 (API REST + WebSocket, nhận MQTT, PostgreSQL), bộ test, và cấu hình triển khai Docker Compose (`backend/deploy/`, gồm cả trang web tĩnh) |
| [`app/`](app/) | Ứng dụng Android (Kotlin, Jetpack Compose). Flavor `mock` chạy dữ liệu giả, flavor `live` nối với backend |

Firmware của datalogger nằm ở kho riêng và không thuộc kho này.

## Bắt đầu

- Backend: xem [`backend/README.md`](backend/README.md) (chạy trên máy, chạy test, triển khai lên máy chủ) và
  [`backend/docs/SECURITY.md`](backend/docs/SECURITY.md).
- App: xem [`app/README.md`](app/README.md). Địa chỉ backend cho flavor `live` đặt trong `app/local.properties`
  (`ggp.api.baseUrl.live=...`), tệp này không nằm trong kho.
