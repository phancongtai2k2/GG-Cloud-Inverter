# GG Cloud Inverter — hướng dẫn cho Claude Code (cả kho)

Đọc trước khi làm gì: **[`PROGRESS.md`](PROGRESS.md)** — mục tiêu, quyết định đã chốt (mục 2), việc đã xong (mục 3),
**việc tiếp theo (mục 4.5)**, môi trường (mục 5), nợ kỹ thuật (mục 6). App có thêm hướng dẫn riêng ở `app/CLAUDE.md`
(tự nạp khi làm trong `app/`).

## Cách làm việc đã thống nhất với người dùng

- **Điểm cần người dùng quyết: hỏi ngay bằng hộp chọn (AskUserQuestion)**, gộp các câu vào một lần hỏi, phương án khuyên
  dùng đặt đầu và ghi "(Recommended)"; trả lời xong thì làm tiếp, không dừng lại ở một danh sách câu hỏi bằng chữ.
  Ghi quyết định vào `PROGRESS.md` mục 2 (ngày, nội dung).
- **Mỗi bước một commit**, message tiếng Việt dạng `feat(backend): B1 …` / `fix(app): …`. Hash của commit được ghi vào
  bảng `PROGRESS.md` ở commit kế tiếp (không amend để sửa hash).
- **Test phải xanh trước khi commit**: backend `dotnet test` (gồm `ContractTests`), app cổng kiểm tra ở `PROGRESS.md` mục 5.
- **Hợp đồng app ↔ backend** ở `contract/`: đổi API → sửa `contract/openapi.yaml` và ghi `contract/CHANGES.md`.
- **Không deploy lên máy chủ thật, không đổi máy chủ.** Thử bằng Docker trên máy; thay đổi cần người vận hành làm thì ghi
  vào `backend/docs/DEPLOY.md`.
- **Push chỉ khi người dùng yêu cầu**, lên remote `platform` = `https://github.com/chienbv2/Inverter-Platform---GG-Cloud`
  (repo của người dùng). Không đẩy lên `origin` (`phancongtai2k2/GG-Cloud-Inverter`) trừ khi được bảo.
- Viết trả lời cho người dùng bằng tiếng Việt.

## Nâng cấp backend (PROGRESS mục 4.4)

- Đặc tả + trình tự: `backend/docs/KE_HOACH_NANG_CAP_BACKEND.docx` (đọc: `pandoc -t plain --columns=200 <file>`).
- Đã xong A0–A7, B0–B7, C2; giai đoạn C còn lại **tạm dừng** đến khi đo 2 instance trên máy chủ thật — hỏi người dùng
  trước khi làm (có chi phí hạ tầng; quyết định D5–D8 hỏi bằng popup).
- Kết quả đo: `backend/docs/LOADTEST.md`; metrics: `backend/docs/METRICS.md`; triển khai / vận hành: `backend/docs/DEPLOY.md`.
- Bản đồ code nhiều instance: `Infrastructure/JobLeader.cs` (leader job), `Shared.cs` (interface + bản trong RAM),
  `RedisShared.cs` / `RedisLive.cs` (bản Redis, pub/sub, live state chung), `Roles.cs` (vai trò api / ingest / jobs),
  `DeviceEventQueue.cs`, `GgMetrics.cs`; `Api/Common.cs` (`IFileStore`, `S3FileStore`); `Program.cs` chọn
  `Shared:Provider` = memory | redis, `Storage:Provider` = local | s3 khi resolve.
