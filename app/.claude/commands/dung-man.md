---
description: Dựng một màn hình theo thiết kế (vd. /dung-man 16b)
argument-hint: <số màn, vd. 16b hoặc 12a>
---
Dựng màn **$ARGUMENTS**.

1. Tìm màn $ARGUMENTS trong `docs/design/screens.md` để biết tên file, route, mục tài liệu và các ảnh trạng thái phụ.
2. Mở ảnh `docs/design/screens/<File>.png` (và các ảnh `<File>__*.png` nếu có). Đọc `docs/design/html/<File>.dc.html` để lấy giá trị chính xác, text tiếng Việt và dữ liệu mẫu trong khối `<script>` cuối file.
3. Đọc mục đặc tả tương ứng trong `docs/spec/dac-ta-chuc-nang-v0.6.md`; nếu mục "Thay đổi trong phiên bản 0.6" hoặc một ADR trong `docs/adr/` nói tới màn này thì theo đó. Tra `docs/spec/MAU_THUAN_v0.6.md` xem màn có mâu thuẫn đã biết không.
4. Trước khi code: liệt kê ngắn các phần tử, trạng thái (loading / rỗng / lỗi / có dữ liệu) và tương tác của màn. Chờ tôi xác nhận nếu có điểm chưa rõ.
5. Dựng màn theo `AGENTS.md`: dùng component và token có sẵn, chuỗi trong `strings.xml`, dữ liệu qua `Fake…Repository`, có `@Preview`. Logic nghiệp vụ đặt trong `domain/` kèm unit test.
6. Chạy `./gradlew assembleDebug testMockDebugUnitTest testLiveDebugUnitTest lint`. So Preview/ảnh chụp 390×844 dp với PNG thiết kế và liệt kê chỗ còn khác.
7. Đề xuất commit message dạng `feat(<feature>): màn $ARGUMENTS …` và chờ tôi đồng ý trước khi commit.
