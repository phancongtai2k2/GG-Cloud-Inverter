# Prompt mẫu cho AI agent (Claude Code, Antigravity, Copilot…)

Với Claude Code có sẵn lệnh tắt: `/dung-man <số màn>` (ví dụ `/dung-man 16b`) và `/review-dot`.

Dán lần lượt từng prompt vào agent. Chờ agent xong, xem ảnh chụp / emulator, sửa nếu cần, commit, rồi sang prompt tiếp theo.

## Bước 0 — Khởi tạo project

```
Đọc AGENTS.md, docs/design/screens.md, docs/design/navigation.md và docs/design/tokens/tokens.md.
Tạo project Android theo mục 2–3 của AGENTS.md (package vn.ggpower.inverter).
Làm: theme từ docs/design/tokens/compose (GgColors.kt, GgDimens.kt), Type.kt với Montserrat,
Theme.kt light/dark; khung Navigation Compose với tất cả route trong screens.md (mỗi màn tạm là placeholder có tiêu đề);
thanh thẻ nổi cho cấp tài khoản và cấp dự án đúng như ảnh docs/design/screens/Projects.png và Main.png;
các helper định dạng số/thời gian ở mục 5 AGENTS.md kèm unit test.
Chưa dựng chi tiết màn nào. Chạy build + test, chụp màn hình Projects và Main placeholder để tôi xem.
```

## Bước 1 — Component dùng chung

```
Dựng các component trong ui/components dựa trên cách chúng xuất hiện trong ảnh và HTML:
Card, SectionHeader (chữ hoa giãn chữ), SegmentedControl, FilterChip, Toggle (52×32), Slider có nhãn,
PrimaryButton / SecondaryButton / DashedButton, BottomSheet, Timeline24h (thanh 24h có đoạn qua nửa đêm, chú giải, mốc giờ),
DayChips (T2…CN, CN chữ đỏ khi không chọn). Mỗi component có @Preview. Không dựng màn.
```

## Mẫu prompt cho một màn

```
Dựng màn <SỐ> · <TÊN> (route `<ROUTE>`).
Thiết kế: docs/design/screens/<FILE>.png, giá trị chính xác trong docs/design/html/<FILE>.dc.html
(khối <script> cuối file có dữ liệu mẫu và logic prototype).
Hành vi: docs/spec/dac-ta-chuc-nang-v0.6.md mục <MỤC>; nếu mục "Thay đổi trong phiên bản 0.6" hoặc một ADR trong docs/adr/ có nói tới màn này thì theo đó.
Dữ liệu qua Fake<Name>Repository. Dùng component có sẵn.
Xong thì chụp màn hình 390×844 dp, so với PNG và liệt kê chỗ còn khác.
```

## Thứ tự đề xuất

| Đợt | Màn | Ghi chú cho prompt |
|---|---|---|
| 1 | 1 Đăng nhập | Nút Đăng nhập tạm điều hướng sang Dự án. |
| 1 | 2 Dự án (có dữ liệu + rỗng) | Hai trạng thái trong một màn: Projects.png và ProjectsEmpty.png. |
| 1 | 4 Màn hình chính | Làm theo từng phần: đầu trang + sơ đồ dòng năng lượng → box Chế độ + box Pin → bộ chọn Ngày/Tháng/Năm → 3 biểu đồ có thanh kéo (component chung). |
| 1 | 16 Cấu hình dự án | Vai trò A/B/C quyết định nhóm tham số (tweak `role`, §16.1). Box Khung giờ theo Thay đổi 0.6 §2. Ghi cấu hình theo ADR-001 (danh sách tham số được ghi, hộp xác nhận, đọc lại). |
| 1 | 16a Danh sách chiến lược, 16b Chi tiết chiến lược | Viết domain chiến lược/khung giờ + unit test **trước** (Thay đổi 0.6 §3–5, ADR-002: 3+3, bản nháp + nút "Lưu thay đổi", đổi tên), rồi dựng 16a, sau đó 16b với bottom sheet thêm/sửa/xoá khung. Phần chưa có thiết kế theo docs/design/BO_SUNG_v3.md. |
| 2 | 5 Thiết bị, 17 Dữ liệu inverter, 17 Dữ liệu pin | Dùng lại component biểu đồ có thanh kéo (bước 15 phút). |
| 2 | 10 Cảnh báo, 10a Chi tiết cảnh báo, 11 Nguồn năng lượng pin | 10a theo Thay đổi 0.6 §6; biểu đồ 30 phút là component nhỏ riêng. Nút "Tạo yêu cầu hỗ trợ kỹ thuật" truyền `alertId` sang màn 8. |
| 3 | 12a–12d Quên mật khẩu, 13a–13b Đăng ký, 14a–14c Thêm dự án, 18 Quét QR | Mỗi luồng là nested graph với ViewModel dùng chung; dựng component OTP 6 ô và thanh tiến trình dùng chung. QR dùng CameraX + ML Kit (hỏi trước khi thêm). |
| 3 | 3 Tôi, 15 Thông tin tài khoản | Ngôn ngữ: danh sách thả xuống trong màn 3, áp dụng ngay (AppCompatDelegate.setApplicationLocales). Màn 15 không có ngôn ngữ. |
| 4 | 6 Dịch vụ, 7 Khám phá, 8 Hỗ trợ (+ chế độ điền sẵn từ 10a), 8 Chi tiết yêu cầu, 9 Bảo hành | Hotline/email trong Dịch vụ copy được. |

## Prompt review sau mỗi đợt

```
Rà lại các màn của đợt vừa làm: so từng màn với PNG thiết kế, kiểm tra chuỗi đã nằm trong strings.xml,
không còn mã màu hard-code, mọi icon-button có contentDescription, test domain đều chạy.
Liệt kê vấn đề theo mức độ và sửa những lỗi rõ ràng; phần cần tôi quyết thì ghi vào docs/OPEN_QUESTIONS.md.
```
