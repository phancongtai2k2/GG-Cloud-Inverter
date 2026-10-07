# Câu hỏi mở

Agent và team ghi vào đây những điểm cần quyết định. Mỗi mục: ngày, màn / mục đặc tả, câu hỏi, đề xuất. Khi đã quyết: chuyển xuống "Đã quyết" và ghi nơi lưu quyết định (ADR, hợp đồng API…).

## Còn mở

- 2026-09-29 · 16b · Cơ chế nạp lịch của chiến lược mới lúc 00:00 khi gateway mất kết nối (Thay đổi 0.6 §8). Phía backend; app chỉ hiển thị.
- 2026-09-30 · 10a · Bảng ánh xạ bit cảnh báo/bảo vệ (0x200 CurrErrReg, BMS) từ SRNE để thay `[MÃ BIT]`. Demo tạm hiển thị nhãn thô (đặc tả mục 8).
- 2026-09-30 · 10a · Nội dung "Nguyên nhân có thể" và "Hướng xử lý" cho từng mã lỗi do Phòng EMS biên soạn. App dùng dữ liệu mẫu của HTML.
- 2026-10-01 · API · Duyệt `docs/api/config-write.openapi.yaml` (đang nháp 0.2.0; 0.2.0 thêm `settings` chỉ xem và `initiator.display_name`). Chưa duyệt thì app chỉ dùng `Fake…Repository`, không nối backend thật.
- 2026-10-01 · API · Hợp đồng lệch backend V2 (.NET): (a) JSON V2 là camelCase (`SolarJsonContext`), hợp đồng là snake_case; (b) vai trò V2 là Admin / Operator / Viewer toàn hệ thống, chưa có thành viên dự án và vai trò A/B/C theo dự án; (c) `Command.Service` mới là stub `POST /api/commands/{sn}` trả 202, trạng thái Pending/Success/Failed/Timeout, lệnh MQTT chỉ có `action` + `val`; (d) V2 chưa có kiểm tra 11 bước, theo dõi ack, đọc lại, hoàn về. Cần quyết: đổi hợp đồng cho khớp V2 hay chỉnh V2 cho khớp hợp đồng. Phòng EMS đã quyết không đối chiếu ở phía V3 (giữ snake_case, `/api/v1`, `serial`); dev backend xử lý.
- 2026-10-01 · API · Hợp đồng backend ↔ gateway (lệnh xuống gateway, mã lỗi gateway) chưa có.
- 2026-10-01 · API · Xác thực và cấp vai trò (đang giả định JWT), địa chỉ máy chủ, nhà phân phối (cấp B) có bị giới hạn theo dự án được gán không.
- 2026-10-01 · 16 · Thử trên máy thật trước 12/10: E225 có nhận giá trị lớn hơn 7 (bit Thứ Năm–CN) không, và có áp cho khung xả không (MAU_THUAN C8). Nếu E225 chỉ áp cho sạc thì cần thêm cách chặn khung xả vào ngày chưa gán.
- 2026-10-02 · Thiết kế · Prototype dùng 6 màu chưa có trong `tokens.json`: `#eef1ec` / `#9aa59f` (chip ngày không chọn), `#b3261e` (chữ lỗi, nút xoá), `#c9d1cb` (nút chính bị khoá), `#b3bdb7` (vạch mốc thanh trượt), `rgba(14,21,18,0.5)` (lớp phủ sheet). App tạm đặt trong `Theme.kt` (`dangerText`, `disabledFill`, `dayChipOffBg`, `dayChipOffText`, `sliderTick`, `scrim`) và tự chọn giá trị dark. Đề xuất: designer thêm vào tokens (cả light và dark) rồi sinh lại `GgColors.kt`. Màn 18 (nền tối cả ở light) cũng có màu riêng chưa có trong tokens: `qrBackground`, `qrScrim`, `qrFrameFill`, `qrHint`, `qrButton`, và từ review đợt 3 (04/10/2026) thêm `qrContent` `#ffffff` (chữ, icon, nút trắng) và `qrOnContent` `#13201b` (chữ trên nút trắng, giữ tối cả ở giao diện tối).
- 2026-10-02 · 4 · Prototype viết "100%", "50%", "0%" (trục SOC) và "18%" (dải nhận định) không có dấu cách; app theo AGENTS.md §5 ("76 %"). Số âm trong chú giải: prototype dùng dấu trừ "−", app dùng "-" như nhãn trục. Cần designer xác nhận.

- 2026-10-02 · 11 / API · Cách tính tỷ lệ năng lượng trong pin theo nguồn (FIFO hay tỷ lệ — đặc tả §11 "cần chốt"). Phía cloud/EMS; app chỉ hiển thị số backend trả.
- 2026-10-02 · API · Đợt 2 chưa có hợp đồng: danh sách thiết bị, telemetry thời gian thực (WebSocket 5 s), lịch sử theo trường, danh sách / chi tiết cảnh báo, đánh dấu đã xử lý, nguồn năng lượng pin. App dùng `Fake…Repository`.
- 2026-10-03 · 13a · Địa chỉ trang Điều khoản sử dụng và Chính sách bảo mật (mở bằng trình duyệt hay màn trong app). App tạm mở màn chờ.
- 2026-10-03 · 14c · Bảng giá EVN sinh hoạt 6 bậc (đ/kWh) để thay "[GIÁ]", và nguồn cập nhật khi EVN đổi giá (app tự lưu hay backend trả).
- 2026-10-03 · 18 · Ví dụ mã QR trong đặc tả §3.18 `GGP:HYB5K:SN2609A00137:7F` sai checksum: XOR từng byte của `GGP:HYB5K:SN2609A00137` ra `19`, nên app (theo thuật toán) báo "Mã không hợp lệ" với đúng ví dụ này. App theo thuật toán; đề xuất v0.7 sửa ví dụ thành `…:19`. Định dạng tem vẫn là "đề xuất" — Phòng EMS cần chốt trước khi nhà máy in.
- 2026-10-03 · API · Đợt 3 chưa có hợp đồng: gửi mã / kiểm tra mã / đặt lại mật khẩu (12), đăng ký và xác thực số điện thoại (13), kiểm tra serial và tạo dự án kèm ảnh, toạ độ, giá điện (14), hồ sơ tài khoản + 3 công tắc thông báo + ngôn ngữ (3). App dùng `FakeAuthRepository` (mã giả lập 123456) và `FakeProfileRepository`.
- 2026-10-03 · 15 / API · Chưa có hợp đồng: lưu họ tên + tải ảnh đại diện (định dạng, cỡ tối đa, có cắt ảnh không), yêu cầu xoá tài khoản bằng mật khẩu. App dùng `FakeProfileRepository` (mật khẩu mẫu `matkhau123`).
- 2026-10-03 · 15 · Avatar mới chỉ cho chọn ảnh từ thư viện; chụp ảnh mới (đặc tả §3.15 "Chụp mới hoặc chọn từ thư viện") để task sau. Cần thống nhất cách chọn: sheet 2 lựa chọn hay bộ chọn của hệ thống.
- 2026-10-03 · 3 · Ngôn ngữ lưu vào hồ sơ tài khoản để đồng bộ giữa các máy (đặc tả §3.19, MAU_THUAN M7), nhưng chưa rõ khi đăng nhập trên máy mới thì ngôn ngữ của hồ sơ có ghi đè ngôn ngữ đang dùng trên máy không. App hiện chỉ ghi lên hồ sơ, chưa đọc về. Đề xuất: đăng nhập thì áp ngôn ngữ của hồ sơ nếu hồ sơ đã có.
- 2026-10-03 · 7 / API · Nội dung Khám phá (sản phẩm, tin tức, video, ảnh bìa) lấy từ CMS nào; chưa có hợp đồng. App dùng `FakeExploreRepository` (dữ liệu mẫu HTML, giữ nguyên "[DUNG LƯỢNG]", "[DẢI CÔNG SUẤT]", "[CÔNG SUẤT TẤM]", "[26/09/2026]" — đặc tả mục 7 điểm 3), không có trạng thái tải / lỗi.
- 2026-10-03 · 7 · Ô tìm kiếm chưa có thiết kế hành vi. App lọc nội dung của thẻ đang mở (không phân biệt dấu, mọi từ phải khớp, như màn 2); không còn mục thì hiện "Không tìm thấy nội dung phù hợp." (câu chữ tạm, 13/20 `inkMuted` canh giữa). Cần designer xác nhận câu chữ và có tìm gộp cả ba thẻ không.
- 2026-10-03 · 7 / 29 · Chạm một mục ở Khám phá mở màn 29 Chi tiết nội dung (chưa thiết kế) — app tạm mở màn chờ.
- 2026-10-04 · 10 / 10a / API · Chữ cảnh báo backend soạn có số °C nhúng sẵn ("Nhiệt độ pin cao (45 °C)", "vượt 50 °C"…) nên không đổi theo cài đặt °F; ở 10a người dùng chọn °F sẽ thấy biểu đồ °F cạnh câu ghi chú °C. Đề xuất cho hợp đồng cảnh báo: backend soạn chữ theo đơn vị của người dùng, hoặc gửi số nhiệt độ thành trường riêng để app tự định dạng. Biểu đồ 10a cần trường đơn vị (`unit`) tách khỏi nhãn.
- 2026-10-04 · Chung · Phòng EMS duyệt câu chữ `res/values-en/strings.xml` (nháp agent soạn; bảng thuật ngữ ở đầu file). Nhãn tháng trên trục biểu đồ tạm "M1, M3…" (bản Việt "T1").
- 2026-10-04 · Chung · Dấu số theo ngôn ngữ (đặc tả mục 5: tiếng Anh "4.2 kW; 48,000 VND") — đã làm 04/10/2026: `GgFormat.defaultStyle` do `GgPowerTheme` đặt theo ngôn ngữ app; ô nhập công suất, giá điện, kWp cũng đổi dấu theo. Chữ backend soạn sẵn có số (cảnh báo, Khám phá) hiện nguyên văn.
- 2026-10-04 · 16 · Tiếng Anh tràn chữ — đã sửa 04/10/2026: (a) cột nhãn box khung giờ rộng theo nhãn dài nhất, tối thiểu 30 dp như prototype; (b) `ScreenHeader` tự thu nhỏ tiêu đề (22 → tối thiểu 16 sp) chỉ khi tràn. Cần designer xác nhận cách (b) cho bản tiếng Anh.
- 2026-10-03 · 8 / API · Chưa có hợp đồng: danh sách phiếu của tài khoản, gửi phiếu (kèm `alertId` khi tạo từ 10a — backend tự đính kèm serial, firmware, mã lỗi), tải tệp đính kèm (định dạng, cỡ tối đa, số tệp — app tạm cho tối đa 5 ảnh / video), định dạng mã phiếu. App dùng `FakeSupportTicketRepository` (mã giả lập `#HT-ddMM-nnnn`).
- 2026-10-03 · 8 · Câu chữ chưa có trong thiết kế (app tạm dùng, cần designer duyệt): "Vui lòng chọn dự án." / "Vui lòng nhập tiêu đề." / "Vui lòng nhập mô tả." (lỗi hiện sau lần bấm Gửi đầu tiên); ô dự án trống "Chọn dự án"; danh sách rỗng "Bạn chưa gửi yêu cầu nào."; mô tả điền sẵn cho cảnh báo đã kết thúc "…, đã kết thúc lúc HH:mm · dd/MM/yyyy."; dòng tệp đính kèm (icon, tên, nút ✕); gửi lỗi: hộp lỗi trên nút, nút đổi thành "Gửi lại".
- 2026-10-03 · 8 · Tài khoản chưa có dự án (mở từ 14a / 18): ô dự án ẩn, cho gửi loại Kỹ thuật không gắn dự án. Mở từ 6: chọn sẵn dự án đang mở.
- 2026-10-03 · 24 · Màn "Gửi yêu cầu thành công" chưa có ảnh / HTML — app dựng tạm từ component sẵn có theo chữ đặc tả §3.24. Thiếu: thời gian phản hồi của loại Tài khoản / Khác (đặc tả chỉ có "Kỹ thuật: trong 4 giờ làm việc"), kênh GG Power sẽ liên hệ. "Về Dịch vụ" khi màn 8 không mở từ Dịch vụ (10a, 14a, 18): app quay về nơi đã mở màn 8.
- 2026-10-03 · 8 Chi tiết / API · Chưa có hợp đồng: chi tiết phiếu (nội dung, số ảnh / video, các lượt trao đổi), gửi tin nhắn, cách nhận tin mới của bộ phận hỗ trợ (đẩy hay hỏi lại). App dùng `FakeSupportTicketRepository` (t1 theo `TicketDetail.dc.html`; nội dung t2–t4 và giờ gửi là mẫu tạm).
- 2026-10-03 · 8 Chi tiết · Chưa có trong thiết kế (app tạm làm, cần designer duyệt): nút sao chép cạnh mã phiếu + thông báo "Đã sao chép mã yêu cầu" 2 s; phiếu Đã đóng thay ô nhập bằng "Yêu cầu đã đóng. Gửi yêu cầu mới nếu cần hỗ trợ thêm."; tải: vòng xoay; lỗi tải: hộp lỗi + "Thử lại"; gửi tin lỗi: hộp lỗi trên ô nhập, giữ chữ; dòng đính kèm khi thiếu một vế ("N ảnh đính kèm" / "Thông tin hệ thống tự đính kèm"; video cũng tính là "ảnh"); tin ở ngày khác ngày gửi phiếu ghi "dd/MM HH:mm". Dòng "N ảnh đính kèm" chưa chạm để xem ảnh (chưa có thiết kế xem ảnh).
- 2026-10-03 · 9 / API · Chưa có hợp đồng: bảo hành theo thiết bị của dự án (tên, serial, ngày kích hoạt, hạn — hay gói?), lịch sử bảo hành (loại việc, phiếu #BH-…, hình thức, trạng thái ngoài "Hoàn tất"). App dùng `FakeWarrantyRepository` (dữ liệu `Warranty.dc.html`), tự tính gói từ hai mốc ngày; tình trạng, thanh và "Còn …" tính theo ngày hôm nay của máy.
- 2026-10-03 · 9 · Ngưỡng "Sắp hết hạn" 90 ngày vẫn chờ GG Power chốt (đặc tả mục 7 điểm 4) — hằng `WARRANTY_SOON_DAYS` ở `domain/warranty`. Tạm chốt 03/10: đúng ngày hết hạn vẫn là Sắp hết hạn, ghi "Hết hạn hôm nay"; sau hạn ghi "Đã hết hạn" (thanh đầy, đỏ); trên 90 ngày ghi "Còn X năm Y tháng" làm tròn tháng gần nhất; dự án chưa có thiết bị: "Chưa có thiết bị nào trong dự án."; lịch sử rỗng: ẩn cả mục. Câu chữ cần designer duyệt.
- 2026-10-03 · 9 / token · Thanh "Sắp hết hạn" trong HTML dùng `#C98A00` — chưa có trong token; app tạm dùng `warning #8A6100` (đậm hơn ảnh). Đề nghị thêm token (vd. `warningStrong`). Chip "Hoàn tất" trong prototype bị kéo cao bằng cả dòng (do flex stretch) — app để chip gọn, canh giữa theo chiều dọc.

- 2026-10-06 · live / API · Bản `live` đã nối toàn bộ repository vào backend ở `../backend` (nhánh `feature/live-backend`); hợp đồng thực tế là các endpoint trong `backend/src/GgPower.Api/Endpoints/` (chưa có file OpenAPI đầy đủ). Cần Phòng EMS / backend duyệt, nhất là phần lệch với `config-write.openapi.yaml`: `blocked_groups[].reason = NOT_SUPPORTED_BY_DEVICE` và `outcome.unsupported`.
- 2026-10-06 · 16 · Datalogger chưa có lệnh ghi SOC dự phòng (E00F): thanh SOC dự phòng luôn bị khoá ở bản `live`. Khung xả đã chạy từ firmware datalogger 1.0.17 (E02D–E033, E03E–E040, E047–E049; đã thử trên máy thật 06/10); thiết bị firmware cũ hơn thì backend chỉ lưu khung xả và app báo "thiết bị này chưa hỗ trợ khung xả" (`write_applied_without_discharge`, cần designer duyệt câu chữ).
- 2026-10-06 · 16 · Datalogger chưa gửi các thanh ghi chỉ xem (E01D–E01F, E20A, E025, bảo vệ, thiết bị): bản `live` ẩn các dòng / mục này (`DeviceSettings.available = false`) thay vì hiện số 0. Cần quyết: firmware gửi thêm hay bỏ khỏi màn.
- 2026-10-06 · 4 · Model màn chính chưa có trạng thái "không có pin": bản `live` hiện thẻ pin 0 % khi inverter không báo pin. Cần thiết kế trạng thái này.
- 2026-10-06 · 14a · Backend chưa có ngày sản xuất và công suất danh định của thiết bị: bản `live` hiện 01/01/1970 và 0 kW ở bước xác nhận thiết bị. Cần nguồn dữ liệu xuất xưởng hoặc bỏ hai dòng này.
- 2026-10-06 · 14a / 18 · Thêm thiết bị hiện chỉ cần serial; cần mã bí mật trong QR để người khác không nhận thiết bị chưa kích hoạt (xem `backend/docs/SECURITY.md`).
- 2026-10-06 · 5, 6 · Datalogger chỉ gửi một công suất tải và một nhiệt độ: các ô tải UPS / tải nhà tách riêng, nhiệt DC-DC / biến áp / môi trường, dữ liệu cell và cờ BMS để trống ở bản `live`.
- 2026-10-06 · 7 · Khám phá ở bản `live` lấy từ backend (`Content/explore.json`), hiện đang rỗng: cần nội dung thật từ Marketing.
- 2026-10-06 · 3 / thông báo · Chưa tích hợp FCM (cần dự án Firebase và `google-services.json`): công tắc thông báo lưu được lên máy chủ nhưng chưa có thông báo đẩy.
- 2026-10-06 · Chung · App giới hạn cỡ chữ hệ thống ở 130 % (`Theme.kt`) vì cỡ lớn hơn làm vỡ ô số liệu; sơ đồ dòng năng lượng tự thu nhỏ ở màn hẹp hơn 390 dp. Cần designer duyệt, và quyết có khoá chiều dọc / làm bố cục tablet không.

- 2026-10-06 · 5, 6, 16, 16b · Theo yêu cầu chủ dự án, app không còn hiển thị mã thanh ghi (dòng monospace dưới tên tham số ở màn Cấu hình và Theo dõi dữ liệu, "E225" ở 16b, "0x210 MachineState", "0x116 / 0x117" ở đồng hồ dòng pin). Khác thiết kế v3; chuỗi `reg_*` và tham số vẫn giữ trong code để bật lại cho bản kỹ thuật nếu cần. Cần designer cập nhật ảnh / prototype.
- 2026-10-06 · 5, 6 · Ở thẻ danh sách hai cột, giá trị dài hơn 22 ký tự (danh sách mã lỗi đang có) hiện ở dòng riêng dưới tên thay vì cùng hàng — cùng hàng thì tên bị ép xuống dòng từng chữ cái. Thiết kế chưa có trường hợp này.

## Việc chưa có người nhận

- 2026-10-02 · Thiết kế · Màn 16: vẽ các trạng thái trong `BO_SUNG_v3.md` §11 (dải khoá ghi, thẻ bị khoá, "Bản demo — chưa mở", thiếu BMS, box TOU không có chiến lược).
- 2026-10-02 · Thiết kế · Thêm nút ← vào đầu trang Main / Device / Service (ADR-003, BO_SUNG_v3.md §10).
- 2026-10-03 · Thiết kế · Bỏ dải "hoặc" và nút "Thêm thiết bị bằng mã QR" khỏi `Login.png` / `Login.dc.html` (ADR-004).
- 2026-10-01 · Thiết kế · Cập nhật prototype 16b và popup theo ADR-002: 3+3, thông báo "Đã đủ 3 khung…", thanh "Lưu thay đổi", chặn Lưu khi không chọn ngày, đổi tên chiến lược; 16: ẩn thẻ "Phát tối đa lên lưới" với cấp A; vẽ các phần trong `BO_SUNG_v3.md`.
- 2026-10-01 · Đặc tả · v0.7: sửa Thay đổi 0.6 §4, §5; §16.1, §16.2, §16.3, §16.4, §16.4a, §16.5, §16.8; mục 8; các mục trong `MAU_THUAN_v0.6.md`; đưa ADR-001, ADR-002, ADR-003, ADR-004 vào thân đặc tả.
- 2026-10-01 · Backend · Trang web admin nhỏ (1 nút bật/tắt ghi) cho công tắc khẩn cấp.
- 2026-10-01 · Ngoài repo · Thư mục cũ "GG Inverter Platform": đánh dấu lưu trữ, ghi chú đầu `CLAUDE.md` (còn quy tắc cấm ghi / trả `READ_ONLY_DEMO`, mâu thuẫn ADR-001).

## Đã quyết

| Ngày | Màn | Câu hỏi | Quyết định | Lưu ở |
|---|---|---|---|---|
| 01/10 | 16b | Đổi tên chiến lược | Có, 1–40 ký tự | ADR-002 mục 5; `BO_SUNG_v3.md` §4 |
| 01/10 | 16b | Số khung tối đa | 3 sạc + 3 xả | ADR-002 mục 1 |
| 01/10 | 16b | Giờ mặc định khi thêm khung | Mặc định §5, trùng thì khoảng trống đầu tiên | ADR-002 mục 6 |
| 01/10 | 16 | Vạch mốc trên thanh SOC dự phòng | Theo ảnh: vạch ở 50 %, nhãn 0 · 20 · 50 · 100 % | ADR-002 mục 11 |
| 01/10 | 16b | Tô đỏ đoạn giao nhau | Không, chỉ hộp lỗi | ADR-002 mục 7 |
| 01/10 | 16b | Không chọn ngày nào | Chặn Lưu, giữ dòng đỏ | ADR-002 mục 3 |
| 01/10 | 16b | Mã hoá giờ `giờ × 256 + phút` | Backend làm; app không có hàm này | ADR-002 mục 17 |
| 01/10 | 16b | Khi nào gửi lệnh ghi lịch | Bản nháp + nút "Lưu thay đổi", một lệnh | ADR-002 mục 2 |
| 01/10 | 16 | Thẻ "Phát tối đa lên lưới" với cấp A | Ẩn (trừ khi đang chạy chế độ đó) | ADR-002 mục 10 |
| 01/10 | 16 | Vai trò được chọn "Phát tối đa lên lưới" | Chỉ cấp B, C | ADR-001 sửa đổi; `writable_modes` |
| 01/10 | 16 | Rời "Phát tối đa" ghi E037 về đâu | Khôi phục giá trị đã lưu, không bao giờ ghi 0 | ADR-001 sửa đổi |
| 01/10 | 16 | Thanh ghi TOU thiếu | Thêm E04D (chỉ bit lưới) và E225 | ADR-001 sửa đổi |
| 01/10 | 16 | Vào TOU | Ghi E039 = 0, E20F = 0 trước lịch | ADR-001 sửa đổi |
| 01/10 | 16 | Ngày không thuộc chiến lược nào | 00:00 ghi E02C = E033 = 0 | ADR-001 sửa đổi; hợp đồng |
| 01/10 | 16 | Sửa lịch TOU khi chế độ khác TOU | Chỉ lưu backend (`device_written = false`) | ADR-001 sửa đổi; ADR-002 mục 14 |
| 01/10 | 16 | Không có BMS | Chặn ghi E00F (`BMS_NOT_CONNECTED`) | Hợp đồng; ADR-002 mục 12 |
| 01/10 | 16 | Đang ở GG AI | Chuyển được sang chế độ khác; SOC, TOU vẫn sửa được | Hợp đồng; ADR-002 mục 13 |
| 01/10 | API | Mô hình ghi | Bất đồng bộ: 202 + hỏi lại 1 s, hết hạn 30 s, `Idempotency-Key` 24 giờ | Hợp đồng |
| 01/10 | API | Hoàn về thất bại / ack không rõ | `device_state = unknown`, app đọc lại và báo | Hợp đồng; `BO_SUNG_v3.md` §2 |
| 01/10 | API | Phát hiện đổi ở màn LCD | `based_on_version` bắt buộc, lỗi `CONFIG_STALE` | Hợp đồng |
| 01/10 | API | Gạt công tắc tắt ghi khi lệnh đang chạy | Chạy nốt và đọc lại, chặn lệnh mới | Hợp đồng |
| 01/10 | API | Khoá 00:00 khi tự nạp lịch | Khoá lệnh người dùng 00:00–00:01, `COMMAND_IN_PROGRESS` | Hợp đồng |
| 01/10 | API | Lỗi Modbus | Một thông báo chung, mã chi tiết chỉ trong nhật ký | Hợp đồng; `BO_SUNG_v3.md` §2 |
| 02/10 | 4, 5, 6 | Về danh sách Dự án từ cấp dự án | Nút ← ở đầu trang Màn hình chính, Thiết bị, Dịch vụ | ADR-003; BO_SUNG_v3.md §10 |
| 02/10 | 4 | Biểu đồ Công suất và Pin ở Tháng / Năm | Mọi biểu đồ đổi theo kỳ. Tháng / Năm: "Năng lượng" (kWh theo ngày / tháng: PV, lưới mua/bán ròng, pin sạc/xả ròng, tải) và Pin (cột sạc / xả + SOC trung bình) | BO_SUNG_v3.md §9 |
| 02/10 | 17 | Biểu đồ lịch sử: chọn trường, khoảng 1 giờ / 7 ngày | Chip bật/tắt, tối đa 3 trường và 2 đơn vị; 1 giờ điểm 1 phút, 24 giờ 15 phút, 7 ngày 1 giờ | `BO_SUNG_v3.md` §12 |
| 02/10 | 5, 17 | Dự án nhiều pack pin | Một thẻ "Pin S16 Pre" cho cả dàn pin của mỗi inverter (số liệu gộp từ SRNE); số pack xem ở nhóm Bảo vệ | OPEN_QUESTIONS (bảng này) |
| 02/10 | 4, 10 | Chấm đỏ trên chuông | Theo số cảnh báo chưa xử lý (không theo "chưa đọc" của §23) | OPEN_QUESTIONS (bảng này); đặc tả v0.7 sửa §23 |
| 02/10 | 10a | "Đánh dấu đã xử lý" | Bật/tắt mọi lúc, mọi vai trò; cảnh báo đang diễn ra vẫn đánh dấu được, khi đó dòng Kết thúc ghi "Đã đánh dấu xử lý" (chữ thường, theo prototype; chốt 03/10) | OPEN_QUESTIONS (bảng này) |
| 02/10 | 17 | "Xem tất cả trạng thái (64 cờ)" chưa có thiết kế | Mở rộng trong thẻ, nhãn thô khi chưa có bảng bit | `BO_SUNG_v3.md` §13 |
| 02/10 | 16b | Định dạng ô công suất trong popup khung | Tự chèn dấu chấm nghìn ngay khi gõ ("3500" → "3.500"), thay cho "định dạng lại khi lưu" của Thay đổi 0.6 §5 | OPEN_QUESTIONS (bảng này); đặc tả v0.7 cần sửa §5 |
| 02/10 | 16 | Tham số chỉ xem và Lịch sử thay đổi chưa có trong hợp đồng | Soạn nháp: `ConfigSnapshot.settings`, `initiator.display_name`; lịch sử dùng `GET /config-commands` | Hợp đồng 0.2.0 (nháp) |
| 02/10 | 16 | `writable_modes` khi `state` khác `enabled` | Giữ theo vai trò, không làm rỗng | Hợp đồng 0.2.0 |
| 02/10 | 16, 16b | Duyệt `BO_SUNG_v3.md` | Duyệt §1–§11 | `BO_SUNG_v3.md` |
| 02/10 | Chung | Chiều cao dòng chữ khác CSS | Sửa toàn app khi review cuối đợt 1 — đã làm: `LineHeightStyle.Trim.None` trong kiểu chữ mặc định | `ui/theme/Type.kt` |
| 01/10 | API | Ai gọi endpoint `/admin` | Cấp C có cờ quản trị ghi, không thêm vai trò | Hợp đồng |
| 03/10 | 1, 18 | Quét QR từ Đăng nhập (chưa đăng nhập) thì gắn thiết bị thế nào | Bỏ nút "Thêm thiết bị bằng mã QR" ở màn Đăng nhập; màn 18 chỉ mở từ 14a | ADR-004 |
| 03/10 | 18 | Thư viện quét QR | CameraX + ML Kit barcode scanning (bản nhúng, chạy offline) | OPEN_QUESTIONS (bảng này) |
| 03/10 | 3 | Ngôn ngữ English khi chưa có bản dịch | Làm cơ chế đổi ngôn ngữ áp dụng ngay; agent soạn `values-en` nháp cho toàn bộ chuỗi, Phòng EMS duyệt câu chữ | OPEN_QUESTIONS (bảng này) |
| 03/10 | 3 | Phạm vi đơn vị °F | Toàn app: mọi chỗ hiện nhiệt độ đổi theo cài đặt qua một hàm định dạng chung; ngưỡng cấu hình thiết bị vẫn theo °C | OPEN_QUESTIONS (bảng này) |
| 04/10 | 5, 10a, 11, 17 | Chi tiết °F | `GgFormat.temperature`: °F giữ số chữ số thập phân như °C ở từng chỗ (42 °C → 108 °F; chú giải 1 số lẻ). Chữ backend soạn có số °C nhúng sẵn (tiêu đề / mô tả / ghi chú / hướng xử lý cảnh báo, kéo theo tiêu đề điền sẵn ở màn 8) hiện nguyên văn. Biểu đồ 10a có trường đơn vị riêng (như prototype `chart.unit`), biểu đồ nhiệt độ đổi theo cài đặt. Hiện chưa có độ chênh nhiệt độ nào; nếu có sau này thì đổi ×9/5 không cộng 32 | OPEN_QUESTIONS (bảng này) |
| 03/10 | 15 | Đổi ảnh đại diện; thông báo sau khi xoá tài khoản | Chỉ chọn từ thư viện (chưa chụp mới); về Đăng nhập kèm snackbar "Đã gửi yêu cầu xoá tài khoản…" | BO_SUNG_v3.md §24 |
