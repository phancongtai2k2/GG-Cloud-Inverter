# Bổ sung giao diện chưa có trong thiết kế v3 (màn 16, 16a, 16b)

- Trạng thái: **Đã duyệt** · 02/10/2026 (chủ sản phẩm), gồm §1–§11. Designer vẽ lại vào prototype; khi prototype cập nhật, prototype thay file này.
- Nguồn: ADR-001, ADR-002, `docs/api/config-write.openapi.yaml`, đặc tả §16.8.
- Nguyên tắc: chỉ dùng component và token có sẵn (`docs/design/tokens/tokens.md`); không thêm màu mới. Khi designer cập nhật prototype, prototype thay file này.

## 1. Hộp xác nhận "cũ → mới" (dùng chung 16 và 16b)

Dialog giữa màn, bo `radius-lg`, nền `surface-raised`, bóng `shadow-overlay`, lề trong 20 dp.

- Tiêu đề (`label`, 600): **"Xác nhận thay đổi"**
- Danh sách dòng thay đổi, mỗi dòng: tên tham số (`body-sm`, `ink-muted`) trên, giá trị `cũ → mới` (`body`, 600) dưới. Cùng kiểu dòng với mục "Lịch sử thay đổi" của màn 16 (ví dụ "SOC dự phòng: 10 % → 20 %").
  - 16: "Chế độ vận hành: Tự tiêu thụ tối đa → Ưu tiên sạc pin"; "SOC dự phòng: 20 % → 30 %".
  - 16b: một dòng cho mỗi khung thêm / sửa / xoá ("Sạc 2: chưa có → 13:00 – 16:00 · 100 % · 3.000 W", "Xả 1: 09:00 – 12:00 → đã xoá"), đổi tên ("Tên: Chiến lược C → Mùa mưa"), đổi ngày ("Ngày áp dụng: T2–T6 → T2–T5"); thứ chuyển từ chiến lược khác ghi thêm dòng của chiến lược đó ("Cuối tuần · Ngày áp dụng: T7, CN → CN").
  - Xoá chiến lược: "Xoá chiến lược "Cuối tuần". T7, CN sẽ chạy như Tự tiêu thụ tối đa."
  - Khi dựng (02/10/2026): ngày áp dụng ghi dạng liệt kê như thẻ 16a ("T2, T3, T4, T5, T6", "Hằng ngày", trống → "chưa có") thay cho dạng khoảng "T2–T6" của ví dụ; khung thêm / sửa ghi đủ "giờ · SOC · công suất", khung xoá chỉ ghi giờ; chiến lược mới: "Thêm chiến lược "Chiến lược C"".
- Ghi chú dưới danh sách (`caption`, `ink-muted`): **"Thay đổi được gửi tới hệ thống và chỉ áp dụng sau khi thiết bị xác nhận."**
- Nếu lịch TOU được sửa khi chế độ hiện tại không phải TOU, thêm dòng (`caption`, `info`): **"Chế độ hiện tại không phải Theo khung giờ. Lịch được lưu và sẽ chạy khi bạn chọn chế độ này."**
- Hai nút cuối, cao 48 dp: **"Huỷ"** (SecondaryButton) · **"Xác nhận"** (PrimaryButton).

## 2. Trạng thái của một lệnh ghi

Sau khi bấm Xác nhận, dialog đóng; control vừa đổi giữ **giá trị cũ** (AGENTS.md §7) và hiện trạng thái ngay dưới mục đó (16) hoặc trên thanh đáy (16b):

| Trạng thái lệnh | Hiển thị | Chữ |
|---|---|---|
| `queued`, `writing`, `verifying`, `reverting` | Vòng xoay nhỏ 16 dp + chữ `body-sm`, `ink-muted`; mọi control ghi của màn bị khoá | **"Đang ghi…"** |
| `applied` | Control đổi sang giá trị mới; snackbar 3 s, nền `success-soft` | **"Đã áp dụng."** |
| `applied` + `device_written = false` | Snackbar 4 s, nền `info-soft` | **"Đã lưu lịch. Lịch sẽ chạy khi bạn chọn chế độ Theo khung giờ."** |
| `reverted` | Giữ giá trị cũ; snackbar 5 s, nền `warning-soft` | **"Thiết bị không nhận giá trị mới, đã giữ giá trị cũ."** |
| `failed` + `device_state` `old_unchanged` | Giữ giá trị cũ; snackbar nền `danger-soft` + nút "Thử lại" | **"Chưa thay đổi được. Thử lại sau."** |
| `failed` hoặc `reverted` + `device_state = unknown` | App đọc lại cấu hình; dialog thông báo, một nút "Đã hiểu" | **"Không chắc thiết bị đang chạy giá trị nào. Đã tải lại cấu hình thực tế, hãy kiểm tra lại."** |
| `failure.code = MODBUS_EXCEPTION` | Như `failed` | **"Thiết bị từ chối thay đổi, thử lại sau."** |

## 3. Lỗi khi gửi lệnh (HTTP 4xx, không tạo lệnh)

| `code` | Hiển thị | Chữ |
|---|---|---|
| `DEVICE_READ_ONLY` | Dải trên đầu màn, nền `info-soft`, icon khoá; mọi control ghi bị vô hiệu | **"Thiết bị này chỉ xem."** |
| `WRITE_DISABLED_BY_SERVER` | Như trên | **"Tạm khoá thay đổi cấu hình. Vui lòng thử lại sau."** |
| `GATEWAY_OFFLINE` | Dải nền `warning-soft` (đặc tả §16.8) | **"Mất kết nối với hệ thống — không thể thay đổi cấu hình"** |
| `BMS_NOT_CONNECTED` | Thanh SOC dự phòng mờ, chú thích `caption` màu `warning` ngay dưới (cùng kiểu dòng "BMS đang giới hạn ở 100 A…" của prototype) | **"Chưa có truyền thông với pin (BMS) nên chưa đổi được SOC dự phòng."** |
| `ROLE_NOT_PERMITTED`, `GROUP_NOT_IN_ALLOWLIST` | Snackbar `danger-soft` (không xảy ra nếu app tôn trọng `write_access`) | **"Bạn không có quyền thay đổi mục này."** |
| `CONFIG_STALE` | App đọc lại cấu hình, mở lại hộp xác nhận với giá trị cũ mới đọc; trên đầu dialog thêm dòng `caption`, `warning` | **"Cấu hình vừa được thay đổi ở nơi khác. Hãy xem lại trước khi xác nhận."** |
| `COMMAND_IN_PROGRESS` | Snackbar `warning-soft` | **"Thiết bị đang xử lý một thay đổi khác. Thử lại sau ít phút."** |
| `RATE_LIMITED` | Snackbar `warning-soft` | **"Thao tác quá nhanh. Thử lại sau %d giây."** |
| `VALIDATION_FAILED` | Không xảy ra nếu kiểm tra phía app đúng; snackbar `danger-soft` | **"Dữ liệu không hợp lệ. Hãy kiểm tra lại."** |
| Mất mạng điện thoại | Theo §3.34: dải xám | **"Không có kết nối Internet"** |

Control bị vô hiệu do vai trò hoặc ngoài danh sách cho phép của ADR-001 hiện chú thích `caption`, `ink-muted`: **"Bản demo — chưa mở"** (ADR-001).

## 4. Màn 16b — thanh Lưu và đổi tên

**Thanh đáy cố định** (cao 80 dp, nền `surface-raised`, viền trên 1 dp `line`, lề 20 dp, chừa chỗ để nội dung cuộn không bị che):
- Trái: dòng `body-sm`, `ink-muted` — **"Có thay đổi chưa lưu"** khi có thay đổi, ẩn khi không.
- Phải: PrimaryButton **"Lưu thay đổi"**, khoá khi: không có thay đổi, hoặc chiến lược không có ngày nào, hoặc đang có lệnh ghi.

**Rời màn khi còn thay đổi** (nút quay lại hoặc cử chỉ back) → dialog:
- Tiêu đề **"Bỏ thay đổi?"**, nội dung **"Các thay đổi chưa lưu sẽ mất."**, nút **"Ở lại"** · **"Bỏ thay đổi"** (chữ `danger`).

**Đổi tên chiến lược:** tiêu đề màn (tên chiến lược) có icon bút chì 20 dp bên phải, vùng chạm 48 dp, `contentDescription` "Đổi tên chiến lược". Chạm → bottom sheet (cùng kiểu sheet thêm khung):
- Tiêu đề **"Đổi tên chiến lược"**; ô nhập một dòng, điền sẵn tên hiện tại, đếm ký tự "n/40".
- Nhãn ô nhập (component ô nhập luôn có nhãn): **"Tên chiến lược"**. Bổ sung 02/10/2026 khi dựng 16b.
- Chiến lược mới tạo từ "Thêm chiến lược" có tên mặc định **"Chiến lược C"** (chữ cái theo vị trí, như prototype).
- Lỗi khi trống: **"Tên chiến lược không được để trống."**
- Nút **"Huỷ"** · **"Lưu"** (chỉ cập nhật bản nháp).

## 5. Chuỗi mới cho `strings.xml`

Toàn bộ chữ in đậm trong file này. Khi duyệt xong, agent chép nguyên văn; bản tiếng Anh bổ sung sau.

## 6. Màn 1 · Đăng nhập — lỗi và trạng thái (đặc tả §3.1)

Thêm 02/10/2026. Thiết kế `Login.png` chỉ có trạng thái nhập liệu.

| Khi | Hiển thị | Chữ |
|---|---|---|
| Bấm Đăng nhập, ô tài khoản trống | Viền ô `dangerText`; dòng 12/16 `dangerText` ngay dưới ô | **"Vui lòng nhập email hoặc số điện thoại."** |
| Sai định dạng (không phải email; không phải số di động VN 10 số hoặc +84) | Như trên | **"Email hoặc số điện thoại không hợp lệ."** |
| Mật khẩu trống | Như trên, dưới ô mật khẩu | **"Vui lòng nhập mật khẩu."** |
| Sai thông tin | Hộp lỗi (cùng kiểu sheet 16b) giữa "Quên mật khẩu?" và nút Đăng nhập | **"Email/số điện thoại hoặc mật khẩu không đúng."** |
| Mất mạng | Như trên | **"Không có kết nối Internet"** (§3.34) |
| Lỗi máy chủ | Như trên | **"Có lỗi xảy ra. Vui lòng thử lại."** |
| Đang xử lý | Nút Đăng nhập giữ màu, chữ thay bằng vòng xoay 20 dp trắng, không bấm được | — |

- Lỗi dưới ô tự xoá khi người dùng sửa ô đó; hộp lỗi tự xoá khi sửa bất kỳ ô nào.
- Nút con mắt khi mật khẩu đang hiện: icon con mắt của prototype thêm nét chéo; `contentDescription` "Ẩn mật khẩu" (khi đang ẩn: "Hiện mật khẩu", theo prototype).
- Ghi nhớ phiên: đăng nhập thành công thì lần mở sau vào thẳng Dự án; Đăng xuất (màn 3) xoá phiên.

## 7. Màn 2 · Dự án — trạng thái chưa có trong thiết kế (đặc tả §3.2, §3.34)

Thêm 02/10/2026. Thiết kế có `Projects.png` (có dữ liệu) và `ProjectsEmpty.png` (chưa có dự án).

| Trạng thái | Hiển thị | Chữ |
|---|---|---|
| Đang tải | Khung xương đúng hình ô tìm kiếm và 3 thẻ, màu `surfaceSunken` (§3.34: không dùng vòng quay toàn màn) | — |
| Tìm không thấy | Dưới ô tìm kiếm, chữ 13/20 `inkMuted` canh giữa | **"Không tìm thấy dự án phù hợp."** |
| Lỗi tải | Giữa màn: chữ 15/24 `inkMuted` + SecondaryButton cao 48 dp | **"Không có kết nối Internet"** / **"Có lỗi xảy ra. Vui lòng thử lại."** · nút **"Thử lại"** |
| Chip Offline | StatusChip tông trung tính (`surfaceSunken` / `ink`) | **"Offline"** (§3.2) |
| Chip Lỗi | StatusChip đỏ (`dangerSoft` / `danger`) | **"Lỗi"** (§3.2, mục 4) |
| Chip Đang chờ kết nối | StatusChip xanh dương (`infoSoft` / `info`) | **"Đang chờ kết nối"** (§3.14) |
| Chỉ số chưa có dữ liệu | Giá trị hiện **"—"**, không hiện 0 (§17.5) | — |

- Tìm kiếm: theo tên hoặc địa chỉ, không phân biệt hoa thường và dấu ("an thi" khớp "Nhà Ân Thi"), mọi từ phải khớp.

## 8. Màn 4 · Màn hình chính, phần trên — chữ chưa có trong thiết kế (đặc tả §3.4)

Thêm 02/10/2026. `Main.png` chỉ có trạng thái ban ngày, chế độ Tự tiêu thụ tối đa, sản lượng cao hơn hôm qua.

| Khi | Chữ |
|---|---|
| Sản lượng thấp hơn hôm qua | **"Hôm nay sản lượng thấp hơn %s so với hôm qua."** (phần trăm đậm `primary`, như bản "cao hơn") |
| Bằng hôm qua (chênh < 0,5 %) | **"Hôm nay sản lượng bằng hôm qua."** |
| Hôm qua không có sản lượng | Ẩn dải nhận định |
| Pin không sạc, không xả (\|P\| < 0,05 kW) | **"Không sạc/xả"** dưới công suất pin |
| Tóm tắt box Chế độ — Ưu tiên sạc pin | **"PV sạc đầy pin trước rồi mới cấp tải"** · SOC dự phòng … |
| — Phát tối đa lên lưới | **"PV ưu tiên phát lên lưới"** · SOC dự phòng … |
| — Theo khung giờ (TOU) | **"Sạc và xả theo khung giờ"** · SOC dự phòng … (đặc tả 4.3a muốn hiện khung giờ; làm khi có domain TOU ở màn 16a/16b) |
| — GG AI | **"EMS tự tối ưu theo giá điện và dự báo"** · SOC dự phòng … |
| — Không xác định (thanh ghi không khớp §16.2) | Tên **"Không xác định"**, tóm tắt **"Cấu hình trên thiết bị khác các chế độ của app"** |

- Icon box Chế độ theo chế độ, lấy từ thẻ chế độ của `Config.dc.html` (mặt trời, đồng hồ, cột điện, pin, GG AI).
- Tia sáng: không vẽ khi \|P\| < 0,05 kW; pin sạc → tia từ SmartBox ra pin, xả → ngược lại; lưới mua → vào SmartBox, phát → ra lưới.
- Số phần trăm trong dải nhận định có dấu cách ("18 %") theo AGENTS.md §5 và đặc tả §3.4; prototype viết liền ("18%").

## 9. Màn 4 · Biểu đồ Công suất và Pin ở chế độ Tháng / Năm

Quyết định 02/10/2026: mọi biểu đồ của Màn hình chính đổi theo Ngày / Tháng / Năm (đặc tả 4.4). Prototype chỉ có chế độ Ngày cho hai biểu đồ này; dưới đây là đề xuất đã dựng, chờ designer vẽ lại.

| | Ngày (giữ như prototype) | Tháng / Năm |
|---|---|---|
| Tiêu đề thẻ | **"Công suất"** | **"Năng lượng"** |
| Mỗi điểm | 15 phút | 1 ngày (Tháng) / 1 tháng (Năm), trục x và nhãn như biểu đồ Sản lượng PV |
| 4 đường | PV, Lưới điện, S16 Pre, Tải tiêu thụ (kW) | Cùng 4 màu, đơn vị kWh: PV = sản lượng; Lưới điện = mua (+) / bán (−) ròng; S16 Pre = sạc (+) / xả (−) ròng; Tải tiêu thụ |
| Dòng thời điểm | "24/09 09:41" | "24/09" (Tháng) · "Tháng 09" (Năm) |
| Thẻ Pin S16 Pre | Dòng BMS (A) + SOC (%) | Cột **"Sạc"** (lên, `energyBattery`) và **"Xả"** (xuống, `energyBattery` 45 %) kWh, trục đối xứng quanh 0; **"SOC trung bình"** là vùng tô + đường đứt theo trục phải 0–100 % |

- Cột / điểm chọn sẵn: ngày (hoặc tháng) hiện tại, giống biểu đồ Sản lượng PV; các cột không chọn mờ 45 %.
- kWh từ 100 trở lên làm tròn số nguyên, nhỏ hơn 1 chữ số thập phân.

## 10. Nút quay lại ở màn cấp dự án (ADR-003)

Thêm 02/10/2026. Áp dụng cho `Main.png`, `Device.png`, `Service.png` (thiết kế hiện không có nút ←).

- Đầu trang giống màn con (`TouStrategy.dc.html` `<header>`): cao 64 dp, đệm trái 4 dp; nút ← vùng chạm 48 dp, mũi tên 24 dp màu `ink` (cùng SVG "M19 12H5M11 6l-6 6 6 6"); tiêu đề 22/28 600 cách nút 4 dp; các nút bên phải (chuông) giữ nguyên.
- Nhãn TalkBack: **"Quay lại Dự án"**.
- Bấm → danh sách Dự án (2), bỏ các thẻ của dự án khỏi back stack.

## 11. Màn 16 · hiển thị theo quyền ghi (ADR-001, ADR-002 mục 10–13)

Thêm 02/10/2026. `Config.png` chỉ có trạng thái cấp C đủ quyền.

- **Dải trạng thái ghi** (BO_SUNG §3) nằm trên cùng nội dung, trước "Chế độ vận hành": bo `radius-md`, đệm 10/12 dp, icon khoá 18 dp, chữ `body-sm` cùng màu với nền (`info` trên `info-soft`, `warning` trên `warning-soft`). Khi có dải, mọi thẻ chế độ, thanh SOC dự phòng bị khoá.
- **Thẻ chế độ bị khoá** (không có quyền, đang khoá ghi): mờ 60 % như thẻ GG AI; thẻ đang chọn giữ nguyên độ rõ.
- **"Bản demo — chưa mở"** (`caption`, `ink-muted`): một dòng dưới thẻ "Bảo vệ và khởi động lại" (thẻ duy nhất có control trông bấm được ngoài danh sách cho phép); 5 công tắc mờ 50 %. Các dòng giá trị cấp B / C vốn chỉ hiện số nên không thêm chú thích.
- **Thiếu BMS:** thanh SOC dự phòng mờ 50 %, dòng `caption` màu `warning` ngay dưới nhãn 0 · 20 · 50 · 100 %: **"Chưa có truyền thông với pin (BMS) nên chưa đổi được SOC dự phòng."**
- **Box Khung giờ sạc/xả khi hôm nay không thuộc chiến lược nào:** dòng tên ghi **"Chưa có chiến lược"**, thanh 24h trống, bên dưới dòng **"Chưa có khung sạc hoặc xả nào. Hệ thống chạy như Tự tiêu thụ tối đa cả ngày."** (chữ của 16b, Thay đổi 0.6 §4). Chiến lược có nhưng chưa có khung: giữ tên, cùng dòng chữ trên.
- **Giá trị thanh ghi dạng mã** lấy tên theo đặc tả: E037 "Độc lập / Nối lưới / Chống phát ngược tại tải UPS / Chống phát ngược tại đầu vào / Ghép AC" (§16.5); E215 "Không truyền thông / RS485 / CAN", E201 "Độc lập / Song song một pha / Ba pha" (§16.7); E025 chỉ có tên cho 1 ("1 · Theo BMS"), giá trị 0 và 2 hiện số.

## 12. Màn 17 · Biểu đồ lịch sử (đặc tả §17.4)

Thêm 02/10/2026, đã duyệt. Prototype `DataInverter` / `DataBattery` chỉ có khoảng 24 giờ và chip trường không bấm được.

- **Chọn trường:** chip bấm bật / tắt (đang chọn: nền `ink`, chữ trắng, dấu ✓ như ảnh). Tối đa **3 trường** và tối đa **2 đơn vị** (trục trái = đơn vị của trường chọn đầu tiên, trục phải = đơn vị thứ hai). Khi đã đủ 3 trường, hoặc trường có đơn vị thứ ba, chip còn lại mờ 50 % và không bấm được. Phải còn ít nhất 1 trường.
- **Khoảng thời gian:**

  | Khoảng | Bước điểm | Nhãn trục X |
  |---|---|---|
  | 1 giờ | 1 phút | `:00 · :15 · :30 · :45 · :60` theo giờ hiện tại (ví dụ 09:00 … 10:00) |
  | 24 giờ | 15 phút | `00 · 06 · 12 · 18 · 24` (như ảnh) |
  | 7 ngày | 1 giờ | thứ viết tắt của 7 ngày gần nhất (T2 … CN) |

- **Màu và nét:** trường trên trục phải vẽ nét đứt 4/4 (như Nhiệt độ DC-AC, Dòng pack của ảnh). Màu: Công suất PV `energy-pv`, Công suất tải `energy-load`, Nhiệt độ DC-AC `danger`, Điện áp lưới `energy-grid`, Tần số lưới `strategy-c`; SOC `energy-battery`, Dòng pack `energy-grid`, Nhiệt độ pin `danger`, Chênh áp cell `strategy-d`. Hai màu chưa có trong ảnh (Tần số lưới, Chênh áp cell) là đề xuất.
- Trục tung không ghi số, chỉ ghi đơn vị ở hai góc trên (như ảnh); miền mỗi trục tự chọn số tròn chia 3 khoảng.
- Dòng thời điểm dưới tiêu đề theo điểm đang chọn: 1 giờ / 24 giờ `dd/MM HH:mm`; 7 ngày `dd/MM HH:00`.

## 13. Màn 17 · Pin — "Xem tất cả trạng thái" (đặc tả §17.3 nhóm D)

Thêm 02/10/2026. Prototype có nút "Xem tất cả trạng thái (64 cờ)" nhưng chưa vẽ danh sách.

- Bấm nút → mở rộng ngay trong thẻ (nút đổi thành "Thu gọn"): hai mục con **Cảnh báo (32)** và **Bảo vệ (32)**, mỗi cờ một dòng 13/20: tên (chưa có bảng ánh xạ SRNE thì nhãn thô **"Cờ cảnh báo bit n"** / **"Cờ bảo vệ bit n"**), bên phải "Bật" (chip theo mức độ) hoặc "Tắt" (`ink-muted`).

## 14. Màn 17 · Pin — lệch điện áp và đồng hồ giới hạn (đặc tả §17.3 nhóm A, B)

Thêm 03/10/2026. Prototype chỉ vẽ trạng thái bình thường.

- **Lệch điện áp > 1 V:** hộp đổi nền `warning-soft`, chip vàng **"Cần kiểm tra"** (thay "Bình thường").
- **Đồng hồ hai cung:** nửa trái 0 → giới hạn xả (0x117), nửa phải 0 → giới hạn sạc (0x116); cung giá trị từ đỉnh tới dòng hiện tại. "Chạm giới hạn" khi |dòng| ≥ 98 % giới hạn của chiều đó: cung màu `energy-pv` (cam) và dòng **"Đang bị giới hạn bởi BMS"**. Dưới 0,5 A: dòng **"Đứng yên"**, không vẽ cung. Đang xả trong giới hạn: **"Đang xả · trong giới hạn"**.
- Nhãn hai đầu dùng dấu trừ "-" như các số âm khác của app (prototype viết "−", xem câu hỏi mở về dấu trừ).

## 15. Màn 10 · Cảnh báo — bộ lọc không có kết quả

Thêm 03/10/2026. Khi bộ lọc đang chọn không có cảnh báo nào: một dòng giữa màn, chữ `body` `ink-muted`, đệm 32 dp: **"Không có cảnh báo nào."** Chip lọc vẫn hiện với số 0.

## 16. Màn 11 · Nguồn năng lượng pin — các trạng thái ngoài ảnh

Thêm 03/10/2026, **đã duyệt** 03/10/2026 (chủ sản phẩm). Ảnh `BatteryEnergy.png` chỉ có trạng thái đang sạc.

- **Chip trạng thái** (cùng ngưỡng 0,05 kW với Màn hình chính):
  - Đang sạc: "Đang sạc +1,1 kW", sắc thái Success (giữ như ảnh).
  - Đang xả: "Đang xả -0,8 kW", sắc thái Info (xanh dương).
  - Đứng yên: "Không sạc/xả", sắc thái Neutral (chuỗi đã dùng ở Màn hình chính).
- **Thông số pin:** pin đang xả thì nhãn đổi thành "Dòng xả", giá trị không dấu. Còn lại giữ "Dòng sạc".
- **Nhãn bên khối pin:**
  - Vạch dẫn đặt giữa lớp, đo tại giữa mặt bên, không xuống thấp hơn vị trí nhãn "Từ lưới" của ảnh.
  - Hai lớp mỏng thì nhãn "Từ PV" được đẩy lên, để hai vạch cách nhau tối thiểu 52 dp.
  - Lớp 0 kWh không có nhãn.
- **Chưa có số liệu theo nguồn** (cloud chưa tính): khối pin kính rỗng; "Dung lượng khả dụng" và bảng chi tiết hiện "—". Các thông số khác vẫn hiện.
- **Chế độ tối:** chưa có thiết kế. Kính lấy `surface-raised`, viền `line-strong`; mặt bên / mặt trên của hai lớp giữ màu light.

## 17. Luồng 12 · Quên mật khẩu — các trạng thái ngoài ảnh

Thêm 03/10/2026, **đã duyệt** 03/10/2026 (chủ sản phẩm).

- **12a, điền sẵn:** ô "Email hoặc số điện thoại" lấy chữ người dùng đã gõ ở Đăng nhập. Ô trống thì để trống (ảnh dùng giá trị mẫu).
- **12a, lỗi định dạng:** dùng lại câu của Đăng nhập, hiện dưới ô: "Vui lòng nhập email hoặc số điện thoại." / "Email hoặc số điện thoại không hợp lệ."
- **12a, quá số lần gửi mã** (3 lần / 10 phút, đặc tả §3.12): hộp lỗi đỏ phía trên nút, cùng kiểu Đăng nhập: **"Bạn đã yêu cầu mã 3 lần trong 10 phút. Vui lòng thử lại sau."** Lỗi mạng / máy chủ dùng câu chung của app.
- **12b, nơi nhận mã khi là email:** giữ ký tự đầu và tên miền, ví dụ **"d•••@ggp.vn"**. Số điện thoại theo ảnh: "0912 ••• 678".

## 18. Bước nhập mã (12b, 13b) — các trạng thái ngoài ảnh

Thêm 03/10/2026, **đã duyệt** 03/10/2026 (chủ sản phẩm). Ảnh chỉ có trạng thái đã nhập đủ 6 số và đang đếm ngược.

- **Ô mã:** ô trống viền 1 dp `line-strong`; ô đã có số và ô đang nhập viền 2 dp `primary` (như ảnh); khi có lỗi cả 6 ô viền 2 dp `danger-text`.
- **Nút Xác nhận / Xác thực:** chỉ bật khi đủ 6 số; chưa đủ thì dùng màu nút bị khoá (`disabledFill`).
- **Hết đếm ngược:** ẩn câu "Gửi lại mã sau …"; liên kết "Gửi lại mã" đổi sang màu `primary`. Gửi lại thành công thì xoá mã đang nhập và đếm lại 60 giây.
- **Hộp lỗi đỏ ngay dưới 6 ô** (cùng kiểu hộp lỗi của Đăng nhập):
  - Mã sai: **"Mã không đúng. Còn 4 lần thử."**
  - Mã quá 5 phút: **"Mã đã hết hạn. Bấm Gửi lại mã để nhận mã mới."**
  - Sai 5 lần liên tiếp: **"Bạn đã nhập sai 5 lần. Vui lòng thử lại sau 15 phút."**
  - Gửi lại quá 3 lần / 10 phút: dùng câu của §17.

  Sửa mã thì hộp lỗi ẩn.

## 19. Màn 12c · Mật khẩu mới — các trạng thái ngoài ảnh

Thêm 03/10/2026, **đã duyệt** 03/10/2026 (chủ sản phẩm). Ảnh chỉ có mức "Tốt".

- **Thanh độ mạnh** (4 vạch), cập nhật khi gõ:

  | Mức | Điều kiện | Số vạch | Màu |
  |---|---|---|---|
  | Yếu | Chưa đạt quy tắc (dưới 8 ký tự hoặc thiếu chữ / số) | 1 | `danger-text` |
  | Trung bình | Đạt quy tắc | 2 | `warning` |
  | Tốt | Đạt quy tắc + 1 trong: chữ hoa lẫn chữ thường, ký tự đặc biệt, từ 12 ký tự | 3 | `primary` |
  | Mạnh | Đạt quy tắc + từ 2 điều trên | 4 | `primary` |

  Ô trống: 4 vạch xám, không có chữ. Mật khẩu mẫu "GGpower2026" ra mức "Tốt" như ảnh.
- **Kiểm tra khi bấm Đổi mật khẩu**, báo dưới ô bằng chữ đỏ 12/16 (cùng kiểu Đăng nhập). Sửa ô thì lỗi ô đó ẩn.
  - Ô trống: "Vui lòng nhập mật khẩu." / "Vui lòng nhập lại mật khẩu."
  - Chưa đạt quy tắc: "Mật khẩu cần tối thiểu 8 ký tự, có cả chữ và số."
  - Không trùng: "Mật khẩu nhập lại không khớp."
- **Phiên đặt lại hết hạn** (backend từ chối mã xác nhận của 12b): hộp lỗi đỏ phía trên nút: "Phiên đặt lại mật khẩu đã hết hạn. Vui lòng quay lại và gửi mã mới."
- **Đổi xong** sang 12d và bỏ 12a–12c khỏi back stack: nút back không quay lại các bước đã xong.

## 20. Màn 13a · Đăng ký — các trạng thái ngoài ảnh

Thêm 03/10/2026, **đã duyệt** 03/10/2026 (chủ sản phẩm).

- **Ô đồng ý mặc định chưa đánh dấu** (prototype để sẵn đã đánh dấu chỉ để chụp ảnh). Chưa đánh dấu: hộp nền trắng viền 2 dp `line-strong`; nút "Tạo tài khoản" khoá nhạt (nền `line`, chữ `ink-muted`, như `Register.dc.html`).
- **Nút "Tạo tài khoản" chỉ phụ thuộc ô đồng ý**, như prototype. Đặc tả §3.13 viết "chỉ bật khi mọi ô hợp lệ và đã đồng ý"; nếu khoá cả khi ô chưa hợp lệ thì người dùng không biết sai ở đâu. Vì vậy bấm nút sẽ kiểm tra từng ô và báo lỗi dưới ô (cùng kiểu Đăng nhập). Sửa ô thì lỗi ô đó ẩn.
- **Câu lỗi dưới ô:**
  - "Vui lòng nhập họ và tên." / "Vui lòng nhập email." / "Vui lòng nhập số điện thoại."
  - "Email không hợp lệ." / "Số điện thoại không hợp lệ."
  - Mật khẩu: dùng câu của §19. Khi ô mật khẩu có lỗi thì ẩn dòng gợi ý "Tối thiểu 8 ký tự…".
- **Email / số đã đăng ký** (backend báo): "Email này đã được đăng ký." / "Số điện thoại này đã được đăng ký." kèm liên kết **"Đăng nhập"** ngay sau câu (đặc tả §3.13). Bấm liên kết: về Đăng nhập, ô tài khoản điền sẵn email / số đó.
- **"Điều khoản sử dụng", "Chính sách bảo mật":** chạm chữ liên kết mở trang tương ứng; chạm phần còn lại của hàng thì đánh dấu / bỏ đánh dấu. Chưa có địa chỉ trang nên tạm mở màn chờ (OPEN_QUESTIONS).
- **"Đã có tài khoản? Đăng nhập"** về Đăng nhập, không điền gì.

## 21. Màn 14a · Thêm dự án – Thiết bị — các trạng thái ngoài ảnh

Thêm 03/10/2026, **đã duyệt** 03/10/2026 (chủ sản phẩm). Ảnh chỉ có trạng thái serial đã kiểm tra hợp lệ.

- **Chưa kiểm tra / đang kiểm tra:** không có thẻ thiết bị; nút "Tiếp tục" khoá nhạt (như nút khoá của `AddProject.dc.html`). Đang kiểm tra: nút "Kiểm tra" hiện vòng quay, giữ nguyên bề rộng.
- **Ô serial:** tự viết hoa và bỏ khoảng trắng khi gõ (tem in chữ hoa, không dấu cách — đặc tả §3.18). Sửa serial sau khi đã kiểm tra thì bỏ thẻ thiết bị, khoá lại "Tiếp tục".
- **Lỗi dưới ô** (chữ đỏ 12/16, ô viền đỏ; thay chỗ dòng gợi ý "Serial in trên tem…"):
  - Trống: "Vui lòng nhập số serial."
  - Không có trong danh sách xuất xưởng: "Không tìm thấy thiết bị có số serial này. Kiểm tra lại tem trên inverter."
  - Thuộc tài khoản khác (đặc tả §3.14): "Thiết bị đang thuộc một tài khoản khác." và liên kết **"Liên hệ hỗ trợ"** bên dưới, mở màn 8 không gắn dự án (người dùng chưa có dự án này).
- **Lỗi mạng / máy chủ:** hộp lỗi đỏ dưới ô serial, câu chung của app.
- **Quét QR:** màn 18 trả serial về 14a, app điền vào ô và kiểm tra ngay.

## 22. Màn 14b · Thêm dự án – Thông tin dự án — các trạng thái ngoài ảnh

Thêm 03/10/2026, **đã duyệt** 03/10/2026 (chủ sản phẩm).

- **Giá trị mặc định:** Tên "Nhà của bạn", Nhà ở, múi giờ Asia/Ho_Chi_Minh (đặc tả §3.14); ngày lắp đặt là hôm nay; địa chỉ và kWp để trống.
- **Kiểm tra khi bấm Tiếp tục**, báo lỗi dưới ô (chữ đỏ 12/16, ô viền đỏ). Sửa ô thì lỗi ô đó ẩn.
  - "Vui lòng nhập tên dự án." / "Tên dự án tối đa 40 ký tự."
  - "Vui lòng nhập địa chỉ."
  - "Ngày lắp đặt không được sau hôm nay." (hộp chọn ngày cũng không cho chọn ngày tương lai)
  - "Vui lòng nhập công suất PV lắp đặt." / "Công suất phải lớn hơn 0."
- **Ô kWp:** chỉ nhận số và một dấu phẩy (gõ dấu chấm tự đổi thành phẩy), tối đa 2 chữ số lẻ.
- **Nút định vị:**
  - Lần đầu: hộp giải thích "Cho phép truy cập vị trí" (Để sau · Tiếp tục), rồi mới tới hộp xin quyền của hệ thống (đặc tả §34). Chỉ xin vị trí gần đúng.
  - Đang lấy: nút hiện vòng quay.
  - Lấy được: dòng xanh "Đã lấy toạ độ 20,9512, 105,9283" thay chỗ dòng gợi ý. Nếu ô địa chỉ đang trống thì điền địa chỉ tra từ toạ độ; ô đã có chữ thì giữ nguyên.
  - Vị trí máy đang tắt: "Vị trí của máy đang tắt. Bật vị trí rồi thử lại."
  - Bị từ chối quyền: "Chưa có quyền vị trí. Bật quyền trong Cài đặt hoặc nhập địa chỉ." kèm liên kết "Mở Cài đặt".
  - Lỗi khác: "Không lấy được vị trí. Thử lại hoặc nhập địa chỉ."
- **Múi giờ:** danh sách thả xuống: Asia/Ho_Chi_Minh, Asia/Bangkok, Asia/Phnom_Penh, Asia/Vientiane, Asia/Jakarta, Asia/Singapore, Asia/Kuala_Lumpur. Mục đang chọn chữ `primary` 600.
- **Ngày lắp đặt:** hộp chọn ngày Material, nền `surface-raised`, ngày chọn nền `primary`; tiêu đề "Ngày lắp đặt", dòng lớn dạng dd/MM/yyyy. Tên thứ / tháng theo ngôn ngữ của máy.
- **Ảnh công trình đã chọn:** ảnh xem trước cao 120 dp bo 10 (cắt vừa khung), nút ✕ tròn trắng 28 dp góc trên phải (vùng chạm 48 dp) để bỏ ảnh; chạm ảnh để chọn ảnh khác.

## 23. Màn 14c · Thêm dự án – Giá điện — các trạng thái ngoài ảnh

Thêm 03/10/2026, **đã duyệt** 03/10/2026 (chủ sản phẩm).

- **"Bỏ qua" và "Tạo dự án" đều tạo dự án** rồi mở Màn hình chính của dự án mới (đặc tả §3.14 "Có thể bỏ qua và cài sau"). "Bỏ qua" không lưu giá điện. Đang tạo: nút vừa bấm hiện vòng quay, nút còn lại khoá.
- **Bậc thang EVN:** thẻ hiện Bậc 1, Bậc 2 và dòng "… · 6 bậc" như prototype. Giá hiện "[GIÁ] đ/kWh" cho tới khi Phòng EMS cung cấp bảng giá (OPEN_QUESTIONS).
- **Ô tiền** (giá cố định, giá bán lên lưới): chỉ nhận số, tự chèn dấu chấm ngăn nghìn khi gõ ("2500" → "2.500"). Giá bán lên lưới để trống coi như 0.
- **Lỗi ô Giá cố định** (khi chọn "Giá cố định", bấm Tạo dự án): "Vui lòng nhập giá điện." / "Giá điện phải lớn hơn 0."
- **Đủ 10 dự án** (đặc tả §3.14): hộp lỗi đỏ phía trên hai nút: "Tài khoản đã có tối đa 10 dự án. Liên hệ GG Power để nâng giới hạn." Lỗi mạng / máy chủ dùng câu chung của app.
- **Sau khi tạo:** quay lại từ Màn hình chính thì về danh sách Dự án; dự án mới có chip "Đang chờ kết nối" và các chỉ số "—" cho tới gói dữ liệu đầu tiên.

## 24. Màn 15 · Thông tin tài khoản — các trạng thái ngoài ảnh

Thêm 03/10/2026, **đã duyệt** 03/10/2026 (chủ sản phẩm).

- **Ảnh đại diện:** bấm avatar mở bộ chọn ảnh của hệ thống (chỉ chọn từ thư viện; chụp mới để sau — OPEN_QUESTIONS). Ảnh đã chọn hiện tròn, cắt vừa khung 88 dp, và tính là thay đổi chưa lưu. Thẻ hồ sơ màn 3 cũng hiện ảnh này sau khi lưu.
- **Nút "Lưu thay đổi"** chỉ bật khi họ tên (bỏ khoảng trắng đầu / cuối) hoặc ảnh khác bản đã lưu. Khi bị khoá: nền `line`, chữ `ink-muted`. Đang lưu thì hiện vòng quay.
- **Tên trống**, bấm Lưu: ô viền đỏ, dòng "Vui lòng nhập họ và tên." dưới ô. Sửa ô thì lỗi ẩn.
- **Lưu xong:** ở lại màn, hiện snackbar xanh "Đã lưu thay đổi." trong 3 giây. Lỗi mạng hoặc máy chủ: snackbar đỏ, dùng câu chung của app, và giữ nguyên thay đổi.
- **Rời màn khi còn thay đổi** (nút ← hoặc Back): hộp "Bỏ thay đổi?" giống 16b (§4).
- **Email / Số điện thoại / Đổi mật khẩu** mở màn 25 / 26 (chưa thiết kế: màn chờ).
- **Hộp xoá tài khoản:** rộng theo màn, cách mép 20 dp. Nút "Xoá tài khoản" khoá nhạt khi ô mật khẩu trống. Sai mật khẩu: "Mật khẩu không đúng." dưới ô. Lỗi mạng / máy chủ: câu chung, cũng hiện dưới ô. Đang gửi thì không đóng được hộp.
- **Xoá thành công:** đăng xuất, xoá back stack, về Đăng nhập. Màn Đăng nhập hiện snackbar "Đã gửi yêu cầu xoá tài khoản. Dữ liệu sẽ bị xoá sau 30 ngày." trong 4 giây.
