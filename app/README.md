# Bộ bàn giao thiết kế — App Inverter dân dụng GG Power (Android)

Bộ này dùng để AI agent (Claude Code, Google Antigravity, Copilot…) viết code giao diện theo đúng thiết kế đã duyệt.
Bản v3 ngày 30/09/2026: 30 màn, 42 ảnh (gồm các trạng thái phụ như popup, tweak, danh sách mở).

## Có gì bên trong

| Đường dẫn | Nội dung |
|---|---|
| `CLAUDE.md` | Claude Code tự đọc file này mỗi phiên; nó nạp `AGENTS.md` và liệt kê lệnh build/test. |
| `.claude/commands/` | Lệnh tắt cho Claude Code: `/dung-man <số màn>`, `/review-dot`. |
| `AGENTS.md` | Quy tắc cho agent: công nghệ, cấu trúc thư mục, giao diện, dữ liệu, test bắt buộc, cách làm việc. |
| `docs/PROMPTS.md` | Prompt mẫu theo từng bước và thứ tự dựng màn. |
| `docs/design/screens/` | Ảnh PNG từng màn và trạng thái phụ (@2x, rộng 390 dp). |
| `docs/design/html/` | Prototype HTML tương tác của từng màn + runtime để xem trên máy (xem README trong đó). |
| `docs/design/screens.md` | Danh sách màn: số, ảnh, route đề xuất, cấp điều hướng, mục đặc tả, đợt làm. |
| `docs/design/navigation.md` | Sơ đồ điều hướng (Mermaid) và bảng liên kết giữa các màn. |
| `docs/design/tokens/` | Design tokens GG Power: `tokens.json`, `tokens.md` (bảng dễ đọc), file Kotlin Compose và Flutter sinh sẵn. |
| `docs/spec/` | Đặc tả chức năng v0.6 (Word + Markdown; mục "Thay đổi trong phiên bản 0.6" ưu tiên hơn thân đặc tả), `MAU_THUAN_v0.6.md` (mâu thuẫn đã biết). Bản v0.5 và `CHANGES_sau_v0.5.md` nằm trong `archive/`. |
| `docs/adr/` | Quyết định kiến trúc/vận hành, ghi đè đặc tả và thiết kế ở điểm liên quan (ADR-001: demo điều khiển thật có kiểm soát; ADR-002: lịch TOU và chế độ vận hành sau 01/10; ADR-003: nút quay lại về Dự án ở màn cấp dự án; ADR-004: bỏ nút quét QR ở màn Đăng nhập). |
| `docs/design/BO_SUNG_v3.md` | Đề xuất giao diện cho các phần ADR yêu cầu mà thiết kế v3 chưa có (chờ duyệt). |
| `docs/api/` | Hợp đồng API backend (xem README trong đó: ghi cấu hình đang là nháp, phần khác chưa có). |
| `docs/OPEN_QUESTIONS.md` | Các điểm còn mở cần team quyết. |

## Dùng với Claude Code (VS Code)

1. Giải nén toàn bộ vào gốc repo (giữ `CLAUDE.md`, `AGENTS.md` và thư mục `.claude/` ở gốc). Trên Windows, thư mục `.claude` có thể bị ẩn — bật "Hidden items" để thấy.
2. Mở repo trong VS Code, mở Claude Code (extension hoặc gõ `claude` trong terminal).
3. Chốt công nghệ ở mục 2 của `AGENTS.md` (mặc định Kotlin + Jetpack Compose).
4. Chạy Bước 0 và Bước 1 trong `docs/PROMPTS.md` (nên bật Plan mode – Shift+Tab – để duyệt kế hoạch trước).
5. Dựng từng màn: `/dung-man 1`, `/dung-man 2`, … theo thứ tự đợt trong `docs/design/screens.md`. Gõ `/clear` giữa các màn.
6. Cuối mỗi đợt: `/review-dot`.

## Dùng với Antigravity / agent khác

Agent đọc `AGENTS.md` ở gốc repo; dán prompt từ `docs/PROMPTS.md`.

## Lưu ý

- Ảnh PNG là giao diện đích; HTML dùng để lấy giá trị chính xác; đặc tả v0.6 (mục "Thay đổi trong phiên bản 0.6" ưu tiên) + ADR quy định hành vi.
- Dữ liệu trên thiết kế là dữ liệu mẫu (hôm nay = T5 24/09/2026). Các chỗ `[SỐ SERIAL]`, `[GIÁ]`, `[MÃ GIAO THỨC]`… là placeholder cần dữ liệu thật.
- App không ghi trực tiếp xuống inverter; mọi thay đổi cấu hình đi qua backend EMS và hộp xác nhận.

## Thay đổi so với v2

- Tách luồng nhiều bước thành màn riêng: 12a–12d Quên mật khẩu, 13a–13b Đăng ký, 14a–14c Thêm dự án (Thay đổi 0.6 §9).
- Thêm `CLAUDE.md` và lệnh tắt `/dung-man`, `/review-dot` cho Claude Code.
- Xem mục "Thay đổi trong phiên bản 0.6" trong `docs/spec/dac-ta-chuc-nang-v0.6.md`.
