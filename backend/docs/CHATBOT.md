# Trợ lý chat AI: đã làm gì và hướng triển khai tiếp theo

Cập nhật: 12/10/2026 · Nhánh `feature/chat-assistant` · PR nháp https://github.com/phancongtai2k2/GG-Cloud-Inverter/pull/1

**Trạng thái:** bước 1 (backend) đã code xong và có test. Phần model **đang tạm dừng** theo yêu cầu của người dùng:
chưa có API key, chưa gọi Claude thật lần nào, chưa chọn chắc nhà cung cấp / mức chi phí. Khi chưa cấu hình key, tính năng
chat tự tắt (API trả `503 CHAT_DISABLED`), phần còn lại của backend chạy như cũ.

## 1. Mục tiêu

Người dùng app Android hỏi bằng tiếng Việt về:
- dữ liệu thiết bị của chính họ (công suất, sản lượng, pin, cảnh báo, thống kê theo ngày / tháng / năm);
- thời tiết tại công trình;
- luật, nghị định, thông tư về năng lượng ở Việt Nam.

## 2. Quyết định đã chốt (12/10/2026)

| Nội dung | Quyết định |
|---|---|
| Kiến trúc | Model gọi công cụ (tool calling) ở **backend**; app chỉ gọi API của backend, không giữ khoá AI |
| Dữ liệu ở VN | **Chỉ lưu trữ ở VN**: hội thoại, log, (sau này) kho luật nằm trong PostgreSQL trên máy chủ. Được gọi model ở nước ngoài, chỉ gửi phần dữ liệu cần cho câu hỏi, không gửi tên / email / số điện thoại / địa chỉ |
| Nhà cung cấp | Claude (Anthropic), model mặc định `claude-opus-5-5`, đổi bằng cấu hình `Chat:Model` |
| Luật năng lượng | Bước 1 trả lời theo hiểu biết của model, kèm lời nhắc kiểm tra văn bản hiện hành trên vbpl.vn / thuvienphapluat.vn. Tra cứu kho văn bản (RAG) làm ở bước 2 |
| Hội thoại | Không bao giờ cắt / sửa lịch sử đã lưu; đủ 30 câu thì người dùng mở hội thoại mới |

Kiến trúc đầy đủ (sơ đồ, RAG, Android, hai mức lưu dữ liệu): file `plans/kien-truc-chatbot.md` trong thư mục dự án.

## 3. Đã làm (bước 1, backend)

### API (`contract/openapi.yaml`, `contract/CHANGES.md` mục 2026-10-12)

| Phương thức | Đường dẫn | Ý nghĩa |
|---|---|---|
| `POST` | `/api/v1/projects/{projectId}/chat/messages` | Gửi câu hỏi `{text, conversation_id?}` → `{conversation_id, answer, refused}` |
| `GET` | `/api/v1/projects/{projectId}/chat/conversations` | 50 hội thoại gần nhất của người dùng trong dự án |
| `GET` | `/api/v1/projects/{projectId}/chat/conversations/{conversationId}` | Nội dung hội thoại (`messages`, `can_continue`) |
| `DELETE` | `/api/v1/projects/{projectId}/chat/conversations/{conversationId}` | Xoá hội thoại |

Mã lỗi: `503 CHAT_DISABLED` (chưa có key), `404 CONVERSATION_NOT_FOUND`, `409 CONVERSATION_FULL` (kèm `max_questions`),
`409 CONVERSATION_BUSY` (hai câu cùng lúc trong một hội thoại), `429 CHAT_QUOTA_EXCEEDED` (kèm `max_questions_per_day`),
`502 CHAT_UNAVAILABLE` (model lỗi / quá thời gian), `400 VALIDATION_FAILED` (câu hỏi rỗng hoặc dài quá 2.000 ký tự).

### 6 công cụ chỉ đọc (`Api/Chat/ChatTools.cs`)

Tất cả chỉ đọc dữ liệu của **dự án đang mở**, model không truyền được id nào (không đọc được dự án / thiết bị khác).

| Công cụ | Lấy gì | Dùng lại |
|---|---|---|
| `get_project_info` | Tên, loại công trình, công suất lắp đặt, múi giờ, biểu giá điện (không gửi địa chỉ, chủ sở hữu) | |
| `get_live_status` | Trạng thái hiện tại (PV, tải, lưới, pin, SOC) | `DataEndpoints.OverviewAsync` |
| `get_devices` | Danh sách thiết bị, online / offline | `DataEndpoints.DevicesAsync` |
| `get_energy_stats(period, date)` | Thống kê theo ngày / tháng / năm: sản lượng, tiêu thụ, mua / bán lưới, pin, tiền tiết kiệm | `StatsService.StatsAsync` |
| `get_alerts(only_open)` | Cảnh báo, lỗi thiết bị | |
| `get_weather(days)` | Dự báo 1–7 ngày từ Open-Meteo (toạ độ làm tròn 2 số lẻ) | |

Kết quả mỗi công cụ tối đa 24.000 ký tự; lỗi của công cụ được trả lại cho model để nó nói rõ với người dùng.

### Cách xử lý một câu hỏi (`Api/Chat/ChatService.cs`)

1. Kiểm tra quyền vào dự án (`AccessService.ProjectAsync`), hạn mức ngày, hội thoại có thuộc người dùng không.
2. Đọc lịch sử, **trả kết nối DB trước khi gọi model** (không giữ kết nối trong lúc chờ).
3. Gửi model: system prompt tiếng Việt cố định (được cache), lịch sử nguyên vẹn, câu hỏi kèm thời điểm hiện tại ở dự án.
4. Vòng gọi công cụ, tối đa 8 lượt gọi model / câu.
5. Có câu trả lời mới lưu cả lượt trong **một transaction**. Model từ chối, lỗi hay hết lượt thì không lưu gì, hội thoại
   giữ nguyên.

### Lưu trữ (`Infrastructure/Sql/007_chat.sql`)

- `chat_conversations`: người dùng, dự án, tiêu đề (dòng đầu câu hỏi), số câu đã hỏi.
- `chat_messages`: mọi khối nội dung dạng `jsonb` (cả phần suy luận và chữ ký của model, để gửi lại y nguyên),
  chữ hiển thị cho app, model đã trả lời, số token vào / ra (để theo dõi chi phí).

### Gọi Claude (`Api/Chat/ClaudeLlmClient.cs`)

SDK chính thức `Anthropic` 12.54.1, Messages API: suy luận tự điều chỉnh (adaptive thinking), mức `effort` cấu hình
được, cache system prompt, và **server-side fallback** (câu bị model chính từ chối vì an toàn được model dự phòng trả lời).
Toàn bộ việc gọi model nằm sau interface `ILlmClient` (`Api/Chat/ChatModel.cs`).

### Giới hạn mặc định (`appsettings.json` mục `Chat`)

| Cấu hình | Mặc định |
|---|---|
| `Chat:Model` | `claude-opus-5-5` |
| `Chat:Effort` | `medium` (`low` / `medium` / `high`) |
| `Chat:MaxTokens` | 16.000 |
| `Chat:MaxQuestionsPerConversation` | 30 |
| `Chat:MaxQuestionsPerUserPerDay` | 50 (tính trong 24 giờ) |
| `Chat:MaxModelCallsPerQuestion` | 8 |

### Test

- `ChatTests` (6 test, dùng model giả lập `ScriptedLlm`, không gọi Claude): gửi lại lịch sử nguyên vẹn; người dùng khác
  không xem / hỏi tiếp được hội thoại; công cụ không nhận id; lỗi công cụ trả về model; câu bị từ chối / model lỗi không
  được lưu; hạn mức ngày và trạng thái tắt.
- `ContractTests` khớp đường dẫn API với `contract/openapi.yaml`.
- Toàn bộ: 131/133 xanh. 2 test hỏng là test S3 trong `MultiInstanceTests` (cần SeaweedFS, không có trong máy thử),
  đã hỏng từ trước khi thêm chat.

### Triển khai

`backend/docs/DEPLOY.md` mục "Trợ lý chat (Claude)": đặt `ANTHROPIC_API_KEY` trong `.env` (không bao giờ đưa vào app hay
repo), tuỳ chọn `CHAT_MODEL`, `CHAT_MAX_PER_DAY`; máy chủ cần ra được `api.anthropic.com` và `api.open-meteo.com`.

## 4. Chưa làm / cần biết

- **Chưa gọi Claude thật lần nào** (chưa có key). Chất lượng trả lời tiếng Việt, độ chính xác số liệu và chi phí thực
  chưa được đo.
- **Chưa có màn chat trên app Android.**
- **Chi phí:** API Anthropic tính tiền riêng theo token, không dùng gói Claude Pro / Max hiện có; nạp tiền và theo dõi ở
  console.anthropic.com. Giá Opus 5.5: 4 USD / 1 triệu token vào, 20 USD / 1 triệu token ra. Ước tính (chưa đo)
  khoảng 0,05–0,10 USD / câu hỏi có gọi công cụ; mức tối đa của một người dùng khoảng 50 câu / ngày. Model rẻ hơn
  (Sonnet, Haiku) chỉ cần đổi `CHAT_MODEL`.
- **Chuyển dữ liệu ra nước ngoài:** dù chỉ gửi phần dữ liệu cần thiết, vẫn là có truyền dữ liệu ra nước ngoài; nên hỏi
  người phụ trách pháp lý về hồ sơ đánh giá tác động (Nghị định 13/2023, Luật Bảo vệ dữ liệu cá nhân 2025). Đây là suy
  luận, không phải tư vấn pháp lý.
- **Đổi nhà cung cấp model** (ví dụ LLM chạy trong VN): viết một lớp mới cài `ILlmClient` (khoảng 100 dòng) và đăng ký
  thay `ClaudeLlmClient` trong `Program.cs`, không chỉ là đổi cấu hình. Hội thoại cũ có phần suy luận riêng của Claude
  nên không gửi tiếp được sang model khác: khi đổi, chỉ cho hỏi tiếp ở hội thoại mới (hoặc bỏ các khối `thinking` khi gửi).
- Chưa có streaming: app nhận câu trả lời một lần khi xong (thường vài giây đến vài chục giây khi gọi nhiều công cụ).

## 5. Hướng triển khai tiếp theo (đề xuất theo thứ tự)

### Bước 0: chốt phương án model (người dùng quyết)

1. Chọn nhà cung cấp và model: giữ Claude (Opus / Sonnet / Haiku), hay LLM chạy trong VN (dịch vụ cloud VN hoặc tự host
   model mở bằng vLLM). Nếu chọn LLM trong VN: viết `ILlmClient` mới, kiểm tra model đó gọi công cụ ổn định.
2. Tạo API key trên máy chủ (biến `ANTHROPIC_API_KEY` hoặc key của nhà cung cấp mới), đặt hạn mức chi tiêu ở console.
3. Chạy thử 20–30 câu hỏi thật trên dữ liệu dev để đo chất lượng, thời gian trả lời và chi phí / câu; chỉnh
   `Chat:Effort`, `MaxQuestionsPerUserPerDay` theo kết quả.

### Bước 1b: màn chat trên app Android

- Màn chat Jetpack Compose mở từ màn dự án; gọi `POST .../chat/messages` bằng JWT hiện có, lưu `conversation_id`.
- Danh sách hội thoại, xoá hội thoại, nút "Hội thoại mới" khi `can_continue = false` hoặc gặp `409 CONVERSATION_FULL`.
- Thông báo riêng cho từng mã lỗi (`CHAT_DISABLED` thì ẩn tính năng, `CHAT_QUOTA_EXCEEDED` báo hết lượt hôm nay,
  `refused = true` hiện câu từ chối của app).
- Render markdown, gợi ý câu hỏi mẫu ("Hôm nay hệ thống phát được bao nhiêu kWh?", "Ngày mai có nắng không?").
- Làm theo cách thống nhất trong `app/CLAUDE.md` (so Light + Dark, cổng kiểm tra ở `PROGRESS.md` mục 5).

### Bước 2: tra cứu luật năng lượng (RAG)

1. Đổi image Postgres sang có pgvector; bảng `law_documents` (số hiệu, tên, cơ quan, ngày hiệu lực, tình trạng, link
   nguồn) và `law_chunks` (Điều / Khoản, nội dung, vector 1024 chiều, `tsvector`).
2. Container `text-embeddings-inference` chạy **bge-m3** trên máy chủ (văn bản không ra nước ngoài).
3. Job nhập 20–50 văn bản cốt lõi từ vbpl.vn / congbao.chinhphu.vn (Luật Điện lực 2024, Luật Sử dụng năng lượng tiết
   kiệm và hiệu quả, nghị định / thông tư về điện mặt trời mái nhà, DPPA, giá điện…); danh sách cần rà theo nghiệp vụ.
4. Công cụ `search_energy_law(query)`: tìm kiếm lai vector + full-text, gộp bằng RRF, ưu tiên văn bản còn hiệu lực.
   Câu trả lời bắt buộc trích số hiệu và Điều / Khoản, không tìm thấy căn cứ thì nói rõ.
5. Job hằng tuần cập nhật văn bản mới / sửa đổi / hết hiệu lực.

### Bước 3: chất lượng và vận hành

- Bộ 50–100 câu hỏi mẫu kèm đáp án để chấm trước mỗi lần đổi prompt hoặc model.
- Streaming câu trả lời qua WebSocket sẵn có (từng đoạn chữ, rồi một tin "done").
- Metrics: số câu, token vào / ra, chi phí theo ngày, tỉ lệ lỗi (`GgMetrics`, `backend/docs/METRICS.md`).
- Gửi kèm ngữ cảnh màn hình (đang xem thiết bị nào) để hiểu câu "thiết bị này".
- Nhiều instance: hạn mức và hội thoại đã nằm trong PostgreSQL nên chạy được trên nhiều instance API, không cần thêm gì.

## 6. Bản đồ code

| File | Nội dung |
|---|---|
| `backend/src/GgPower.Api/Chat/ChatModel.cs` | `ChatOptions`, `ChatBlock`, `ChatTurn`, `ILlmClient` |
| `backend/src/GgPower.Api/Chat/ClaudeLlmClient.cs` | Gọi Claude bằng SDK Anthropic |
| `backend/src/GgPower.Api/Chat/ChatTools.cs` | 6 công cụ, `OpenMeteoWeather` |
| `backend/src/GgPower.Api/Chat/ChatService.cs` | System prompt, vòng gọi công cụ, hạn mức, lưu hội thoại |
| `backend/src/GgPower.Api/Endpoints/ChatEndpoints.cs` | 4 endpoint |
| `backend/src/GgPower.Infrastructure/Sql/007_chat.sql` | Bảng chat |
| `backend/tests/GgPower.Tests/ChatTests.cs` | Test với model giả lập |
