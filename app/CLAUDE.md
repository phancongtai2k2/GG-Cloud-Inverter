# App Inverter dân dụng GG Power — hướng dẫn cho Claude Code

@AGENTS.md

## Lệnh thường dùng
- Build cả hai bản: `./gradlew assembleDebug` (từng bản: `assembleMockDebug` · `assembleLiveDebug`)
- Unit test: `./gradlew testMockDebugUnitTest testLiveDebugUnitTest`
- Lint: `./gradlew lint` (chạy trên bản mặc định `mockDebug`)
- Hai bản build (quy trình B1): `mock` dùng toàn bộ `Fake…Repository`, không cần mạng, applicationId `vn.ggpower.inverter.mock`; `live` gọi backend, địa chỉ đặt ở `local.properties` khoá `ggp.api.baseUrl.live` (không commit). `live` đã nối backend thật; hợp đồng API ở `../contract/` (đổi API → đọc `../contract/CHANGES.md`).
- Xem prototype: `cd docs/design/html && python3 -m http.server 8000` rồi mở `http://localhost:8000/<Tên>.dc.html`

## Nhắc nhanh
- Giao diện đích: `docs/design/screens/*.png` — mở ảnh trước khi code một màn.
- Danh sách màn, route, đợt: `docs/design/screens.md`. Điều hướng: `docs/design/navigation.md`.
- Quyết định mới nhất: `docs/adr/` và mục "Thay đổi trong phiên bản 0.6" của `docs/spec/dac-ta-chuc-nang-v0.6.md` (cả hai ưu tiên hơn thân đặc tả). Mâu thuẫn đã biết: `docs/spec/MAU_THUAN_v0.6.md`.
- Mỗi lần chỉ làm 1 màn; build + test xanh rồi mới commit.
- Gặp điểm chưa rõ: ghi vào `docs/OPEN_QUESTIONS.md` và hỏi, không tự đoán API.
