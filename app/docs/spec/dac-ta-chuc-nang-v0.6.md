**Đặc tả chức năng**

Ứng dụng Android cho hệ inverter dân dụng

| **Mục** | **Nội dung** |
|----|----|
| Tài liệu | Cấu trúc chức năng và mô tả chi tiết các màn hình |
| Phiên bản | 0.6 (chiến lược TOU, chi tiết cảnh báo, tách luồng nhiều bước, 30/09/2026 — xem mục "Thay đổi trong phiên bản 0.6") |
| Nền tảng | Android, khung màn hình 390 × 844 dp |
| Ngôn ngữ giao diện | Tiếng Việt (có tùy chọn ngôn ngữ) |
| Hệ thiết kế | GG Power Design System · font Montserrat · màu thương hiệu xanh \#0F7A3A / \#149C4A, cam \#F5831F |
| Người soạn | Phòng EMS |

# Thay đổi trong phiên bản 0.6

Bản 0.6 đồng bộ đặc tả với thiết kế prototype 30 màn hình (30/09/2026).
Ở những điểm dưới đây, mục này ưu tiên hơn nội dung chi tiết phía sau.

### 1. Chế độ vận hành (màn 16)

- **Bỏ chế độ Dự phòng mất điện.** Còn 5 chế độ: GG AI (pha 2, đang
  khoá), Tự tiêu thụ tối đa, Theo khung giờ (TOU), Phát tối đa lên lưới,
  Ưu tiên sạc pin. Chỉ chọn một chế độ tại một thời điểm.

- **SOC dự phòng (E00F):** thanh trượt 5–100 %, bước 5 %, vùng chạm cao
  44 px, kéo được bằng cảm ứng; dưới thanh hiển thị số giờ dự phòng ước
  tính.

- **Thẻ TOU:** không có nút "Chỉnh sửa lịch".

### 2. Box "Khung giờ sạc/xả" trên màn 16

- Mục riêng, luôn hiển thị, nằm giữa "Chế độ vận hành" và "Pin và ngưỡng
  SOC". Chạm vào box mở màn 16a.

- Nội dung: dòng "Hôm nay" và tên chiến lược áp dụng hôm nay (không có ô
  chữ, không có ngày/thứ, không có nhãn trạng thái); thanh 24h với mốc
  00–06–12–18–24; dòng tóm tắt "N giai đoạn: a lần sạc, b lần xả" và
  từng giai đoạn theo giờ bắt đầu (loại, HH:mm – HH:mm, thời lượng, ghi
  chú "qua đêm"). Không có lịch tuần.

### 3. Màn 16a · Danh sách chiến lược

Chiến lược là một bộ khung sạc/xả kèm tập thứ áp dụng. Tối đa 4 chiến
lược. Mỗi thứ chỉ thuộc tối đa một chiến lược; thứ chưa gán chạy như Tự
tiêu thụ tối đa.

- Mỗi chiến lược là một thẻ: tên, các thứ áp dụng (viết tắt T2…CN, hoặc
  "Hằng ngày" khi đủ 7 ngày), nhãn "Hôm nay", thanh 24h thu nhỏ, dòng
  tóm tắt các giai đoạn. Không có ô chữ A/B và không có khối lịch tuần.

- Nút "Thêm chiến lược" (ẩn khi đủ 4) tạo chiến lược trống và mở 16b.

Dữ liệu mẫu dùng cho prototype và test:

| **Chiến lược** | **Sạc** | **Xả** |
|----|----|----|
| Ngày thường (T2–T6) | 22:00–06:00 (SOC 90 %, 3.000 W); 13:00–16:00 (100 %, 3.000 W) | 09:00–12:00 (50 %, 4.000 W); 18:00–21:00 (20 %, 5.000 W) |
| Cuối tuần (T7, CN) | 00:00–06:00 (90 %, 3.000 W); 14:00–16:00 (100 %, 3.000 W) | 10:00–12:00 (60 %, 4.000 W); 18:00–23:00 (20 %, 5.000 W) |

### 4. Màn 16b · Chi tiết chiến lược

- Tiêu đề là tên chiến lược; quay lại về 16a. Thanh 24h và chú giải: Sạc
  cam \#e0700f, Xả xanh \#149c4a, Tự động xám. Không có box "Tóm tắt
  lịch".

- **Danh sách khung:** chỉ gồm các khung đã đặt, đánh số liên tục theo
  loại (Sạc 1, Sạc 2, Xả 1, Xả 2). Xoá một khung thì khung đó biến mất,
  các khung cùng loại phía sau dồn lên và đánh số lại. Không còn khung
  nào thì hiện dòng "Chưa có khung sạc hoặc xả nào. Hệ thống chạy như Tự
  tiêu thụ tối đa cả ngày."

- **Áp dụng các ngày:** 7 chip. Chọn: nền màu chính, chữ trắng. Không
  chọn: nền \#eef1ec, chữ \#9aa59f, mờ 60 %; riêng CN chữ đỏ \#c62828.
  Chọn thứ đang thuộc chiến lược khác sẽ chuyển thứ đó sang, kèm dòng
  nhắc. Không chọn ngày nào thì hiện dòng đỏ "Chưa chọn ngày nào nên
  lịch sẽ không chạy…" ngay dưới các chip.

- Nút "Thêm khung sạc hoặc xả" (viền đứt), danh sách quy tắc, nút đỏ
  "Xoá chiến lược này" (khi có nhiều hơn 1 chiến lược).

### 5. Popup thêm / sửa khung (thay màn 30)

- **Thêm:** bottom sheet "Thêm khung giờ", chọn Khung sạc / Khung xả.
  Mặc định sạc 10:00–14:00, SOC 80 %, 3.000 W; xả 06:00–08:00, SOC 40 %,
  4.000 W.

- **Sửa:** tiêu đề "Sửa khung Sạc 1", điền sẵn giá trị, có nút đỏ "Xoá
  khung này"; các khung khác trên thanh 24h mờ 35 %.

- Trường: Giờ bắt đầu, Giờ kết thúc (hh:mm), SOC dừng (%), Công suất tối
  đa (W); khung sạc có dòng "Nguồn sạc: lưới điện". Khi lưu, công suất
  được định dạng lại có dấu chấm nghìn.

Kiểm tra theo thứ tự; lỗi hiện trong hộp đỏ và khoá nút Lưu:

| **\#** | **Điều kiện** | **Thông báo** |
|----|----|----|
| 1 | Đã đủ khung cùng loại (tối đa 2) | Đã đủ 2 khung sạc. Hãy sửa hoặc xoá một khung có sẵn. |
| 2 | Sai định dạng giờ | Giờ phải có dạng hh:mm, ví dụ 01:30. |
| 3 | Giờ bắt đầu = giờ kết thúc | Giờ kết thúc phải khác giờ bắt đầu. |
| 4 | SOC ngoài 0–100 | SOC dừng phải trong khoảng 0–100 %. |
| 5 | Công suất ngoài 0–12.000 W | Công suất tối đa phải trong khoảng 0–12.000 W. |
| 6 | Trùng khung khác, tính cả khung qua nửa đêm; chạm mép không tính là trùng | Trùng với Xả 1 (09:00 – 12:00). Các khung không được chồng lấn. |

### 6. Chi tiết cảnh báo (10a) và yêu cầu hỗ trợ

- Chạm một cảnh báo ở màn 10 mở màn 10a (thực hiện mục 23).

- 10a gồm: icon, chip mức độ (Lỗi/Cảnh báo/Thông tin) và chip trạng thái
  (Chưa xử lý/Đã xử lý); tiêu đề, mô tả; bảng Thiết bị · Dự án · Bắt đầu
  · Kết thúc ("Đang diễn ra" chữ đỏ hoặc "Tự phục hồi lúc …") · Thời
  lượng; Mã lỗi (mã thô, tên tiếng Việt, vị trí bit chờ xác nhận với
  SRNE); biểu đồ diễn biến 30 phút có vạch đứt tại thời điểm cảnh báo
  (cảnh báo thông tin không có biểu đồ); Nguyên nhân có thể; Hướng xử lý
  đánh số.

- Nút chính "Tạo yêu cầu hỗ trợ kỹ thuật" mở màn 8; nút phụ "Đánh dấu đã
  xử lý" bật/tắt.

- Màn 8 mở từ 10a: dải đỏ "Tạo từ cảnh báo · \<tiêu đề\> · \<thời
  điểm\>", ghi chú serial, firmware và mã lỗi được đính kèm tự động;
  loại Kỹ thuật, dự án của cảnh báo, tiêu đề "\<tiêu đề cảnh báo\> –
  \<tên dự án\>", mô tả điền sẵn. Quay lại về 10a.

### 7. Ngôn ngữ

- Bỏ màn 19 Chọn ngôn ngữ. Chọn ngôn ngữ ở màn 3 Tôi, mục Cài đặt hệ
  thống: dòng "Ngôn ngữ" mở danh sách thả xuống với 2 lựa chọn Tiếng
  Việt / English (chỉ tên); lựa chọn hiện tại nền xanh nhạt và dấu ✓;
  chọn xong tự đóng và áp dụng ngay.

- Màn 15 Thông tin tài khoản không có mục Ngôn ngữ.

### 8. Ràng buộc phần cứng cho backend / EMS

- Inverter SRNE chỉ có một bộ khung TOU (E026–E04D) và một mặt nạ ngày
  E225, không lưu được nhiều chiến lược. Backend nạp khung của chiến
  lược áp dụng cho ngày mới xuống inverter lúc 00:00 khi ngày mới thuộc
  chiến lược khác ngày trước. E02C/E033 tự bật/tắt theo mục 16.4a.

- **Chưa chốt:** xử lý khi gateway mất kết nối lúc chuyển ngày. App chỉ
  hiển thị, không tự ghi.

### 9. Tách luồng nhiều bước thành màn riêng

| **Luồng** | **Màn** | **Điều hướng** |
|----|----|----|
| Quên mật khẩu (12) | 12a Tìm tài khoản · 12b Mã xác thực · 12c Mật khẩu mới · 12d Thành công | Gửi mã → Xác nhận → Đổi mật khẩu → Về Đăng nhập. Quay lại: 12b→12a, 12c→12b, 12a/12d→Đăng nhập |
| Đăng ký (13) | 13a Thông tin · 13b Xác thực OTP | Tạo tài khoản (khoá khi chưa đồng ý điều khoản) → Xác thực → Dự án (rỗng). Quay lại 13b→13a |
| Thêm dự án (14) | 14a Thiết bị · 14b Thông tin dự án · 14c Giá điện | Tiếp tục (khoá khi chưa kiểm tra serial) → Tiếp tục → Tạo dự án / Bỏ qua → Màn hình chính. Quay lại 14b→14a, 14c→14b |

- Mỗi màn giữ thanh tiến trình ở đầu, tô đến bước hiện tại. Dữ liệu nhập
  giữ trong ViewModel dùng chung cho cả luồng (nested navigation graph).

### 10. Câu hỏi còn mở

- Gateway mất kết nối lúc 00:00; đổi tên chiến lược; giữ 2+2 hay mở 3+3
  khung; giờ mặc định khi thêm khung; bảng ánh xạ bit lỗi SRNE; nội dung
  nguyên nhân / hướng xử lý cho từng mã lỗi.

# Thay đổi trong phiên bản 0.5

Bản 0.5 cập nhật màn hình Cấu hình dự án (16) và lịch theo khung giờ sau
khi rà soát prototype tương tác.

- **Tách lịch TOU sang màn 16a:** Lịch sạc và xả không còn nằm trên
  màn 16. Thẻ chế độ Theo khung giờ (TOU) có nút Chỉnh sửa lịch, mở màn
  con 16a Lịch theo khung giờ (mục 16.4).

- **Tự bật/tắt lịch theo chế độ:** Bỏ hai công tắc Bật lịch sạc (E02C)
  và Bật lịch xả (E033) khỏi giao diện. Người dùng bật hoặc tắt lịch
  bằng cách chọn chế độ vận hành; app và backend tự ghi hai thanh ghi
  này theo quy tắc ở mục 16.4a.

- **Chọn ngày áp dụng:** Chip ngày không áp dụng hiển thị mờ; riêng Chủ
  nhật có chữ đỏ khi không áp dụng.

# Thay đổi trong phiên bản 0.4

Bản 0.4 bổ sung các màn hình còn thiếu sau khi rà soát bản thiết kế 22
màn hình trên canvas. Các màn hình mới được đánh số từ 20 đến 35 ở mục 3
và gắn nhãn "mới v0.4" trong cây chức năng. Mức ưu tiên:

- **P1 — thiếu trong luồng chính (20–24):** Kết nối Wi-Fi cho GG
  SmartBox, Cài đặt dự án, Thành viên và phân quyền, Chi tiết cảnh báo,
  Gửi yêu cầu thành công.

- **P2 — nút đã có trên thiết kế nhưng chưa có màn đích (25–30):** Đổi
  email / số điện thoại, Đổi mật khẩu, Thêm thiết bị vào dự án, Cài đặt
  thông báo cảnh báo, Chi tiết nội dung Khám phá, Sửa khung sạc/xả.

- **P3 — thường có ở app inverter (31–35):** Cập nhật firmware, Báo cáo,
  Danh sách dự án cho nhà phân phối, Trạng thái đặc biệt và lần đầu mở
  app, Giới thiệu ứng dụng.

- **Cập nhật màn hình có sẵn:** Màn hình chính có box Chế độ hoạt động
  đặt ngay trên box Pin S16 Pre (mục 4.3a), thay cho nút nhỏ ở hàng tên
  dự án. Mục 7 bổ sung 4 điểm cần xác nhận.

# 1. Tổng quan

Ứng dụng giúp chủ nhà theo dõi và quản lý hệ thống điện mặt trời có lưu
trữ (PV + inverter hybrid GG SmartBox + pin S16 Pre) của GG Power. Người
dùng xem dòng năng lượng theo thời gian thực, sản lượng và tiết kiệm,
tình trạng thiết bị, cảnh báo, đồng thời tiếp cận dịch vụ hậu mãi (hỗ
trợ, bảo hành, nội dung sản phẩm).

### Đối tượng sử dụng

- **Chủ hộ gia đình** sở hữu một hoặc nhiều hệ thống (dự án).

- **Chủ cơ sở nhỏ** (ví dụ nhà xưởng) có hệ công suất lớn hơn, quản lý
  nhiều dự án trong cùng tài khoản.

- **Nhà phân phối của GG Power** cần quản lý các dự án mà họ đã lắp đặt
  cho Khách hàng để theo dõi tình trạng hoạt động và hỗ trợ Khách hàng
  khi cần.

# 2. Cấu trúc chức năng

Ứng dụng có hai cấp điều hướng. Mỗi cấp có một thanh thẻ nổi (floating
tab bar) riêng ở cạnh dưới màn hình. Các màn hình con không có thanh thẻ
và luôn có nút Quay lại ở góc trên bên trái.

### 2.1 Cây chức năng

- **Màn hình Đăng nhập**

  - Màn hình Quên mật khẩu

  - Màn hình Đăng ký mới

- **Cấp tài khoản - thanh thẻ: Dự án - Tôi**

  - Màn hình Dự án (danh sách hệ thống)

    - Màn hình Thêm dự án mới (mở từ nút + Thêm ở góc trên cùng bên phản
      màn hình)

      - Màn hình Kết nối Wi-Fi cho GG SmartBox (bước cuối của Thêm dự
        án; hoặc từ Thiết bị → Đổi mạng Wi-Fi) — mới v0.4

    - Màn hình Danh sách dự án cho nhà phân phối (biến thể của màn Dự án
      theo vai trò) — mới v0.4

  - Màn hình Tôi (thông tin tài khoản tài khoản & cài đặt cơ bản cho ứng
    dụng)

    - Màn hình Thông tin tài khoản (mở từ nút Thông tin tài khoản)

      - Màn hình Đổi email / Đổi số điện thoại — mới v0.4

      - Màn hình Đổi mật khẩu — mới v0.4

    - Màn hình Giới thiệu ứng dụng (mở từ thẻ Tôi) — mới v0.4

- **Cấp dự án (sau khi chọn một dự án) - thanh thẻ: Màn hình chính -
  Thiết bị - Dịch vụ**

  - Màn hình chính (giám sát năng lượng)

    - Màn hình Nguồn năng lượng pin (mở từ box SOC pin)

    - Màn hình Cảnh báo (mở từ nút chuông)

      - Màn hình Chi tiết cảnh báo (chạm vào một cảnh báo) — mới v0.4

      - Màn hình Cài đặt thông báo cảnh báo (nút cài đặt ở Cảnh báo) —
        mới v0.4

    - Màn hình Cấu hình dự án (mở từ nút chế độ hoạt động)

      - Màn hình Sửa khung sạc/xả và hộp xác nhận thay đổi — mới v0.4

    - Màn hình Cài đặt dự án (nút bánh răng ở đầu Màn hình chính) — mới
      v0.4

      - Màn hình Thành viên và phân quyền (mở từ Cài đặt dự án) — mới
        v0.4

    - Màn hình Báo cáo (mở từ khu vực biểu đồ ở Màn hình chính) — mới
      v0.4

  - Màn hình Thiết bị

    - Màn hình Theo dõi dữ liệu (mở từ thiết bị có trong dự án)

    - Màn hình Thêm thiết bị vào dự án (nút quét QR ở Thiết bị) — mới
      v0.4

    - Màn hình Cập nhật firmware (từ thẻ thiết bị hoặc cảnh báo firmware
      mới) — mới v0.4

  - Màn hình Dịch vụ

    - Nút Khám phá (Sản phẩm · Tin tức · Hướng dẫn)

      - Màn hình Chi tiết sản phẩm / bài viết / video — mới v0.4

    - Nút Yêu cầu hỗ trợ (Hỗ trợ kỹ thuật, phản hồi)

      - Màn hình Gửi yêu cầu thành công — mới v0.4

    - Nút Bảo hành (Thông tin bảo hành, lịch sử bảo hành)

- Màn hình dùng chung - không thuộc cấp nào

  - Màn hình Thêm thiết bị bằng mã QR (gọi từ Đăng nhập và từ Thêm dự án
    mới)

  - Chọn ngôn ngữ: danh sách thả xuống trong màn Tôi (màn 19 đã bỏ,
    v0.6)

  - Trạng thái đặc biệt và lần đầu mở app (mất kết nối, không có dữ
    liệu, xin quyền) — mới v0.4

# 3. Mô tả chi tiết từng màn hình

## 1. Đăng nhập

| **Thuộc tính** | **Giá trị**                            |
|----------------|----------------------------------------|
| Mục đích       | Xác thực người dùng trước khi vào app. |
| Thanh thẻ      | Không                                  |
| Điều hướng     | Đăng nhập thành công → Dự án (2)       |

### Thành phần

- Logo GG Power ở đầu màn hình; tiêu đề "Đăng nhập"; mô tả ngắn "Theo
  dõi điện mặt trời, pin lưu trữ và tiền điện tiết kiệm của bạn."

- Ô **Email hoặc số điện thoại** (một ô nhập chung cho cả hai).

- Ô **Mật khẩu**, có nút con mắt để hiện/ẩn mật khẩu.

- Liên kết **Quên mật khẩu?** (chưa có màn hình).

- Nút chính **Đăng nhập**.

- Nút phụ **Thêm thiết bị bằng mã QR** (chưa có luồng).

- Dòng "Chưa có tài khoản? **Đăng ký**" (chưa có màn hình) và khẩu hiệu
  "GO GREEN GO GLOBAL".

### Quy tắc & trạng thái cần bổ sung khi phát triển

- Kiểm tra định dạng email/số điện thoại; báo lỗi ngay dưới ô nhập.

- Trạng thái đang xử lý trên nút Đăng nhập; thông báo sai thông tin đăng
  nhập.

- Ghi nhớ phiên đăng nhập; lần mở sau đi thẳng vào Dự án.

## 2. Dự án

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Liệt kê các hệ thống (dự án) thuộc tài khoản và cho phép chọn dự án để giám sát. |
| Thanh thẻ | Cấp tài khoản: Dự án (đang chọn) · Tôi |
| Điều hướng | Bấm thẻ dự án → Màn hình chính (4) của dự án đó |

### Thành phần

- Tiêu đề "Dự án" và nút **+** (Thêm dự án) ở góc phải.

- Ô tìm kiếm "Tìm theo tên hoặc địa chỉ".

- Danh sách thẻ dự án. Mỗi thẻ gồm: icon loại công trình (nhà ở / nhà
  xưởng), tên dự án, địa chỉ (có icon vị trí), cấu hình (ví dụ "Inverter
  hybrid 5 kW · S16 Pre"), chip trạng thái.

- Mỗi thẻ có hàng chỉ số nhanh: **PV hiện tại** (kW), **Hôm nay** (kWh
  sản lượng), **SOC pin** (%).

### Dữ liệu mẫu

| **Dự án** | **Địa chỉ** | **Cấu hình** | **Trạng thái** | **PV / Hôm nay / SOC** |
|----|----|----|----|----|
| Nhà của bạn | Văn Giang, Hưng Yên | Inverter hybrid 5 kW · S16 Pre | Online | 4,2 kW · 18,6 kWh · 76 % |
| Nhà Ân Thi | Mỹ Hào, Hưng Yên | Inverter 8 kW · 2 × S16 Pre | Online | 6,1 kW · 27,4 kWh · 88 % |
| Nhà máy GG Power | Yên Mỹ, Hưng Yên | Inverter 30 kW · 4 × S16 Pre | Cảnh báo | 21,8 kW · 96,2 kWh · 54 % |

### Trạng thái dự án

- **Online** (chip xanh): hệ thống kết nối và hoạt động bình thường.

- **Cảnh báo** (chip vàng): có cảnh báo chưa xử lý.

- Cần bổ sung khi phát triển: **Offline** (mất kết nối), **Lỗi** (chip
  đỏ).

## 3. Tôi

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Thông tin tài khoản, cài đặt thông báo và cài đặt hệ thống của app. |
| Thanh thẻ | Cấp tài khoản: Dự án · Tôi (đang chọn) |
| Điều hướng | Đăng xuất → Đăng nhập (1) |

### Khối tài khoản

- Ảnh đại diện (chữ viết tắt), họ tên, email, số điện thoại.

- Dòng **Thông tin tài khoản** trong cùng box, bấm để xem/sửa hồ sơ
  (chưa có màn hình).

### Thông báo (công tắc bật/tắt)

| **Mục**               | **Mô tả**                             | **Mặc định** |
|-----------------------|---------------------------------------|--------------|
| Cảnh báo thiết bị     | Lỗi, mất kết nối, nhiệt độ cao        | Bật          |
| Báo cáo hằng ngày     | Sản lượng và tiền tiết kiệm lúc 20:00 | Bật          |
| Thông báo từ GG Power | Tin tức, sản phẩm mới từ GG Power     | Tắt          |

### Cài đặt hệ thống

- **Đơn vị nhiệt độ:** bộ chọn dạng nổi (segmented) °C / °F, mặc định
  °C.

- **Giao diện:** Sáng / Tối / Tự động (theo hệ điều hành), mặc định Tự
  động.

- **Ngôn ngữ: bấm để mở danh sách thả xuống Tiếng Việt / English, chọn
  xong áp dụng ngay (v0.6).**

### Khác

- Nút **Đăng xuất** (viền đỏ) ở cuối màn hình.

## 4. Màn hình chính

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Giám sát năng lượng của dự án đang chọn: thời gian thực và lịch sử. |
| Thanh thẻ | Cấp dự án: Màn hình chính (đang chọn) · Thiết bị · Dịch vụ |
| Điều hướng | Chuông → Cảnh báo (10); box Pin S16 Pre → Nguồn năng lượng pin (11) |
| Chiều cao | Màn hình cuộn dọc (~2.250 dp trong bản mẫu) |

### 4.1 Đầu trang

- Tiêu đề "Màn hình chính"; nút **chuông** ở góc phải, có chấm đỏ khi
  còn cảnh báo chưa xử lý.

- Tên dự án ("Nhà của bạn") kèm chip trạng thái **Online**.

- Dòng nhận định nhanh, ví dụ: "Hôm nay sản lượng cao hơn **18 %** so
  với hôm qua."

### 4.2 Sơ đồ dòng năng lượng (thời gian thực)

- Bốn nút năng lượng quanh bộ điều khiển trung tâm **GG SmartBox**:
  **PV** (4,2 kW), **Lưới điện** (0,0 kW), **S16 Pre** (76 %, +1,1 kW,
  Đang sạc), **Tải tiêu thụ** (3,1 kW).

- Đường nối cong mềm màu trắng; hiệu ứng tia sáng màu xanh chạy dọc
  đường nối theo **chiều dòng điện**, đuôi mờ dần. Đường không có dòng
  điện thì không có tia sáng.

- Icon PV là tấm pin mặt trời.

- Quy ước dấu: công suất pin dương (+) là **sạc**, âm (−) là **xả**.

### 4.3a Box Chế độ hoạt động — mới v0.4

- Một box riêng, rộng hết khung, đặt ngay trên box Pin S16 Pre. Gồm icon
  chế độ, nhãn "Chế độ hoạt động", tên chế độ đang chạy (ví dụ **Tự tiêu
  thụ tối đa**) và một dòng tóm tắt (ví dụ "PV cấp tải trước, dư sạc pin
  · SOC dự phòng 20 %").

- Bấm vào box mở Cấu hình dự án (16). Thay thế nút chế độ nhỏ ở hàng tên
  dự án trong bản trước.

- Khi đang chạy lịch TOU hoặc chế độ Dự phòng mất điện, dòng tóm tắt
  hiện khung giờ hoặc SOC giữ tương ứng.

### 4.3 Box Pin S16 Pre

- SOC hiện tại (76 %) và thanh năng lượng trong pin, chia theo nguồn:
  **Từ PV** 10,2 kWh, **Từ lưới** 1,9 kWh.

- Bấm vào box để mở màn hình Nguồn năng lượng pin (11).

### 4.4 Bộ chọn khoảng thời gian

- Bộ chọn dạng nổi **Ngày / Tháng / Năm**, kèm điều hướng ‹
  ngày/tháng/năm › (ví dụ "24/09/2026", "Tháng 09/2026", "Năm 2026").

- Mọi biểu đồ và chỉ số tổng hợp bên dưới cập nhật theo khoảng thời gian
  đang chọn.

### 4.5 Biểu đồ Công suất

- Biểu đồ đường gồm 4 chuỗi: **PV** (cam), **Lưới điện** (xanh dương),
  **S16 Pre** (xanh lá), **Tải tiêu thụ** (xám).

- Chú thích xếp 2 × 2, mỗi mục có giá trị hiện tại cùng hàng với tên.

### 4.6 Biểu đồ Pin S16 Pre

- Hai chuỗi: **Dòng BMS** (A, trục −40 … 40, dương là sạc) và **SOC**
  (%).

- Giá trị hiện tại: Dòng BMS +21,5 A, SOC 76 %.

### 4.7 Biểu đồ Sản lượng PV

- Cột xếp chồng theo 3 phần: **PV tiêu thụ**, **PV sạc pin**, **PV lên
  lưới**. Dữ liệu mẫu: tự tiêu thụ ~82 %, lên lưới \< 2 %, còn lại sạc
  pin.

- Đường nét đứt mờ, bo cong: **Hệ số hiệu quả (kW)** = sản lượng / thời
  gian, vẽ trên trục tung phụ bên phải.

- Hai trục tung (kWh bên trái, kW bên phải); chú thích 2 × 2 phía trên
  biểu đồ.

### 4.8 Chỉ số tổng hợp theo kỳ

| **Chỉ số**          | **Ngày** | **Tháng**   | **Năm**      |
|---------------------|----------|-------------|--------------|
| Tiêu thụ            | 14,2 kWh | 435 kWh     | 5,50 MWh     |
| Tỷ lệ tự dùng       | 82 %     | 82 %        | 82 %         |
| Tiền điện tiết kiệm | 48.000 đ | 1.273.480 đ | 15.043.600 đ |
| Giảm phát thải CO₂  | 10,4 kg  | 274 kg      | 3,24 t       |

## 5. Thiết bị

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Xem tình trạng từng thiết bị trong dự án. |
| Thanh thẻ | Cấp dự án: Màn hình chính · Thiết bị (đang chọn) · Dịch vụ |
| Hành động đầu trang | Nút quét mã QR để thêm thiết bị (chưa có luồng) |

### Thẻ Inverter

- Tên "Inverter hybrid 5 kW", số serial, chip **Online**.

- Chỉ số: Công suất 4,2 kW · Nhiệt độ 42 °C · Firmware v1.3.2.

### Thẻ Pin S16 Pre

- Trạng thái "Đang sạc · đầy sau ~2 giờ".

- SOC 76 % kèm thanh tiến độ.

- Chỉ số: Công suất 1,1 kW · Điện áp 51,2 V · SOH 98 %.

## 6. Dịch vụ

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Điểm vào cho các dịch vụ hậu mãi và liên hệ GG Power. |
| Thanh thẻ | Cấp dự án: Màn hình chính · Thiết bị · Dịch vụ (đang chọn). Icon: bàn tay nâng trái tim |
| Điều hướng | Khám phá (7) · Hỗ trợ (8) · Bảo hành (9) |

### Box "Hỗ trợ GG Power" (đầu màn hình)

- **Hotline** (icon điện thoại) và **Email** (icon phong bì), mỗi dòng
  có nút **sao chép**.

- Bấm sao chép: nội dung được đưa vào bộ nhớ tạm; icon đổi thành dấu ✓
  nền xanh và hiện thông báo nhỏ "Đã sao chép số hotline" / "Đã sao chép
  email".

- Giá trị mẫu: 1900 6868 · support@ggpower.vn (**cần thay bằng thông tin
  thật**).

### Mục TIỆN ÍCH

| **Nút**  | **Mô tả phụ**                              | **Mở màn hình**    |
|----------|--------------------------------------------|--------------------|
| Khám phá | Sản phẩm, tin tức và video hướng dẫn       | Khám phá (7)       |
| Hỗ trợ   | Gửi yêu cầu kỹ thuật, tài khoản            | Yêu cầu hỗ trợ (8) |
| Bảo hành | Ngày kích hoạt, hạn và tình trạng bảo hành | Bảo hành (9)       |

## 7. Khám phá

| **Thuộc tính** | **Giá trị**                                                |
|----------------|------------------------------------------------------------|
| Mục đích       | Nội dung marketing và hướng dẫn: sản phẩm, tin tức, video. |
| Loại           | Màn hình con của Dịch vụ; nút Quay lại → Dịch vụ (6)       |

### Thành phần cố định

- Ô **Tìm kiếm** luôn hiển thị.

- Bộ chọn dạng nổi **Sản phẩm / Tin tức / Hướng dẫn**; tiêu đề và bộ
  chọn luôn hiển thị, nội dung bên dưới cuộn dọc.

### Thẻ Sản phẩm

- Danh sách: Pin lưu trữ S16 Pre, GG SmartBox, Inverter hybrid, Tấm pin
  mặt trời, Phụ kiện lắp đặt. Mỗi mục có ảnh/icon, tên và mô tả ngắn
  (các thông số như dung lượng, dải công suất đang để trống, chờ số liệu
  thật).

### Thẻ Tin tức

- Tin **NỔI BẬT** có ảnh lớn (ví dụ "GG Power vươn tầm quốc tế").

- Danh sách bài: tiêu đề, chuyên mục (Hướng dẫn / Mẹo sử dụng) và thời
  gian đọc.

### Thẻ Hướng dẫn

- Danh sách video: ảnh thu nhỏ có thời lượng (ví dụ 3:45), tiêu đề và
  sản phẩm liên quan (GG SmartBox, Ứng dụng GG Power, Pin S16 Pre).

## 8. Yêu cầu hỗ trợ

| **Thuộc tính** | **Giá trị**                              |
|----------------|------------------------------------------|
| Mục đích       | Tạo phiếu hỗ trợ (ticket) gửi GG Power.  |
| Loại           | Màn hình con; nút Quay lại → Dịch vụ (6) |

### Hai thẻ trong màn hình

- Thẻ Gửi yêu cầu mới (mặc định): biểu mẫu mô tả ở phần Trường nhập bên
  dưới.

- Thẻ Yêu cầu của tôi: danh sách phiếu đã gửi, mỗi phiếu gồm mã phiếu,
  tiêu đề, dự án liên quan, ngày gửi và chip trạng thái (Đã tiếp nhận ·
  Đang xử lý · Đã xử lý · Đã đóng).

- Chạm vào một phiếu mở trang chi tiết: nội dung đã gửi, tệp đính kèm và
  các lượt trao đổi với bộ phận hỗ trợ.

- Thẻ Yêu cầu của tôi hiển thị phiếu của mọi dự án thuộc tài khoản,
  không chỉ dự án đang mở.

### Trường nhập

- **Loại yêu cầu:** bộ chọn dạng nổi Kỹ thuật / Tài khoản / Khác (mặc
  định Kỹ thuật).

- **Dự án cần hỗ trợ:** chỉ hiện khi chọn Kỹ thuật. Dạng **danh sách thả
  xuống**: khi đóng hiển thị dự án đang chọn (tên, địa chỉ · inverter,
  chip trạng thái, mũi tên ▾); khi mở hiển thị toàn bộ dự án, dự án đang
  chọn có nền xanh nhạt và dấu ✓; chọn xong danh sách tự đóng.

- **Tiêu đề** (ví dụ "Inverter báo lỗi mất lưới").

- **Mô tả chi tiết**: sự cố, thời điểm, mã lỗi.

- **Đính kèm ảnh hoặc video.**

- Nút **Gửi yêu cầu** ở cuối màn hình.

### Cần bổ sung khi phát triển

- Kiểm tra bắt buộc (tiêu đề, mô tả; dự án khi chọn Kỹ thuật).

- Màn hình xác nhận gửi thành công kèm mã phiếu, sau đó chuyển sang thẻ
  Yêu cầu của tôi với phiếu vừa tạo nằm trên cùng.

- Tự động đính kèm thông tin hệ thống (serial, firmware, cảnh báo gần
  nhất) khi chọn loại Kỹ thuật.

## 9. Bảo hành

| **Thuộc tính** | **Giá trị**                                        |
|----------------|----------------------------------------------------|
| Mục đích       | Tra cứu thông tin bảo hành các thiết bị của dự án. |
| Loại           | Màn hình con; nút Quay lại → Dịch vụ (6)           |

### Thành phần

- Dòng ngữ cảnh: "Dự án **Nhà của bạn** · 3 thiết bị".

- Mỗi thiết bị một thẻ: tên, số serial, chip tình trạng, **Ngày kích
  hoạt**, **Hạn bảo hành**, thanh thời gian bảo hành đã dùng, thời hạn
  gói (ví dụ "Bảo hành 5 năm") và thời gian còn lại.

- Nút **Yêu cầu bảo hành** ở cuối → mở Yêu cầu hỗ trợ (8).

### Tình trạng bảo hành

| **Tình trạng** | **Màu chip** | **Quy tắc gợi ý**                           |
|----------------|--------------|---------------------------------------------|
| Còn bảo hành   | Xanh lá      | Còn trên 90 ngày                            |
| Sắp hết hạn    | Vàng         | Còn 90 ngày trở xuống (ngưỡng cần xác nhận) |
| Hết bảo hành   | Đỏ           | Đã qua hạn bảo hành                         |

### Dữ liệu mẫu

| **Thiết bị** | **Kích hoạt** | **Hạn** | **Gói** | **Tình trạng** |
|----|----|----|----|----|
| Inverter hybrid 5 kW | 15/03/2025 | 15/03/2030 | 5 năm | Còn bảo hành |
| Pin S16 Pre | 15/03/2025 | 15/03/2035 | 10 năm | Còn bảo hành |
| Đồng hồ đo thông minh | 09/11/2024 | 09/11/2026 | 2 năm | Sắp hết hạn (còn 46 ngày) |

## 10. Cảnh báo

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Danh sách sự kiện, lỗi và cảnh báo của dự án. |
| Loại | Màn hình con; mở từ nút chuông ở Màn hình chính; Quay lại → Màn hình chính (4) |
| Hành động đầu trang | Nút cài đặt thông báo |

### Bộ lọc

- Chip lọc: **Tất cả (4)** · **Chưa xử lý (2)** · **Đã xử lý (2)**.

### Mục cảnh báo

- Icon theo mức độ, nhãn mức độ, tiêu đề, mô tả, thời điểm (hh:mm ·
  dd/mm/yyyy) và trạng thái xử lý.

| **Mức độ** | **Màu** | **Ví dụ** |
|----|----|----|
| Lỗi | Đỏ | Mất kết nối lưới điện — inverter chuyển sang cấp điện từ pin |
| Cảnh báo | Vàng | Nhiệt độ pin cao (45 °C); Điện áp PV thấp |
| Thông tin | Xanh dương | Có firmware mới v1.3.3, cập nhật tự động lúc 02:00 |

## 11. Nguồn năng lượng pin

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Cho biết năng lượng đang có trong pin đến từ nguồn nào. |
| Loại | Màn hình con; mở từ box Pin S16 Pre; Quay lại → Màn hình chính (4) |

### Thành phần

- Tên pin và chip trạng thái "Đang sạc +1,1 kW".

- Hai chỉ số lớn: **SOC** 76 %, **Dung lượng khả dụng** 12,1 kWh (pin 16
  kWh).

- Hình khối pin 3D dạng kính, bo góc mềm; bên trong xếp lớp theo nguồn:
  lớp dưới **Từ lưới** (xanh dương), lớp trên **Từ PV** (cam), phần còn
  lại trống. Nhãn ghi bên cạnh khối gồm nguồn, năng lượng (kWh) và phần
  trăm.

- Bảng chi tiết: Từ PV 10,2 kWh (63,8 %) · Từ lưới điện 1,9 kWh (11,9 %)
  · Còn trống 3,9 kWh (24,3 %).

- Thông số pin: Điện áp 51,2 V · Dòng sạc 21,5 A · Nhiệt độ 29 °C · SOH
  98 % · Chu kỳ 312 · Chênh áp cell 12 mV.

### Lưu ý thuật toán

- Tỷ lệ theo nguồn cần tính trên cloud/EMS bằng cách theo dõi năng lượng
  nạp vào pin theo từng nguồn (PV, lưới) và trừ dần khi xả (ví dụ theo
  nguyên tắc FIFO hoặc tỷ lệ). Cần chốt phương pháp tính.

## 12. Quên mật khẩu (tách 12a–12d, v0.6)

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Đặt lại mật khẩu cho tài khoản đã đăng ký, qua email hoặc số điện thoại. |
| Thanh thẻ | Không |
| Điều hướng | Đăng nhập (1) → Quên mật khẩu (12) → Đăng nhập (1) sau khi đổi xong |

### Luồng ba bước

- Bước 1 — Xác định tài khoản: một ô nhập chung Email hoặc số điện
  thoại, nút Gửi mã xác thực.

- Bước 2 — Nhập mã: 6 ô nhập mã OTP, đồng hồ đếm ngược 60 giây, liên kết
  Gửi lại mã (mờ cho tới khi hết đếm ngược).

- Bước 3 — Mật khẩu mới: ô Mật khẩu mới và ô Nhập lại mật khẩu, mỗi ô có
  nút con mắt; chỉ báo độ mạnh mật khẩu; nút Đổi mật khẩu.

- Sau khi đổi thành công: thông báo ngắn, tự chuyển về Đăng nhập sau 2
  giây với ô tài khoản điền sẵn.

### Quy tắc

- Mật khẩu tối thiểu 8 ký tự, có cả chữ và số; ô nhập lại phải trùng
  khớp.

- Mã OTP hết hạn sau 5 phút. Nhập sai 5 lần liên tiếp thì khoá yêu cầu
  trong 15 phút.

- Không tiết lộ tài khoản có tồn tại hay không: dù email/số điện thoại
  chưa đăng ký, vẫn hiện thông báo chung "Nếu tài khoản tồn tại, mã xác
  thực đã được gửi".

- Đổi mật khẩu thành công thì đăng xuất mọi phiên khác trên tất cả thiết
  bị.

- Giới hạn 3 lần gửi mã trong 10 phút cho cùng một tài khoản.

## 13. Đăng ký mới (tách 13a–13b, v0.6)

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Tạo tài khoản mới cho chủ hộ. Tài khoản nhà phân phối do GG Power cấp, không tự đăng ký. |
| Thanh thẻ | Không |
| Điều hướng | Đăng nhập (1) → Đăng ký mới (13) → Dự án (2) ở trạng thái trống |

### Thành phần

- Các ô nhập: Họ và tên, Email, Số điện thoại, Mật khẩu, Nhập lại mật
  khẩu.

- Ô đánh dấu đồng ý Điều khoản sử dụng và Chính sách bảo mật (hai liên
  kết mở trang web).

- Nút chính Tạo tài khoản; dòng "Đã có tài khoản? Đăng nhập" ở cuối.

- Sau khi gửi: màn hình nhập mã OTP xác thực số điện thoại, dùng lại
  thành phần của bước 2 ở màn hình 12.

### Trạng thái sau khi đăng ký

- Vào thẳng Dự án (2) ở trạng thái trống: minh hoạ, dòng "Chưa có dự án
  nào", nút chính Thêm dự án đầu tiên dẫn sang màn hình 14.

### Quy tắc

- Email và số điện thoại là duy nhất trên toàn hệ thống; trùng thì báo
  lỗi ngay dưới ô nhập kèm liên kết Đăng nhập.

- Nút Tạo tài khoản chỉ bật khi mọi ô hợp lệ và đã đánh dấu đồng ý điều
  khoản.

- Tài khoản tạo qua màn hình này mặc định là vai trò Chủ hộ. Nâng lên
  vai trò Nhà phân phối do GG Power thực hiện ở phía quản trị.

## 14. Thêm dự án mới (tách 14a–14c, v0.6)

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Gắn một hệ thống mới vào tài khoản và khai báo thông tin công trình. |
| Thanh thẻ | Không |
| Điều hướng | Dự án (2) → nút + → Thêm dự án mới (14) → Màn hình chính (4) của dự án vừa tạo |

### Bước 1 — Định danh thiết bị

- Nút chính Quét mã QR mở màn hình 18.

- Hoặc ô Số serial nhập tay, kèm gợi ý "Serial in trên tem cạnh phải
  inverter".

- Nút Kiểm tra: gọi backend xác minh serial có trong danh sách xuất
  xưởng và chưa thuộc tài khoản nào.

- Kết quả kiểm tra hiện thẻ tóm tắt: model, công suất định mức, ngày
  xuất xưởng.

### Bước 2 — Thông tin dự án

| **Trường** | **Kiểu** | **Bắt buộc** | **Ghi chú** |
|----|----|----|----|
| Tên dự án | Văn bản | Có | Mặc định "Nhà của bạn" |
| Loại công trình | Chọn một | Có | Nhà ở / Nhà xưởng — quyết định icon trên thẻ dự án |
| Địa chỉ | Văn bản + nút định vị | Có | Nút định vị lấy toạ độ GPS để tra cứu thời tiết và bức xạ |
| Múi giờ | Chọn một | Có | Mặc định Asia/Ho_Chi_Minh |
| Ngày lắp đặt | Ngày | Có | Dùng làm mốc tính bảo hành ở màn hình 9 |
| Công suất PV lắp đặt | Số (kWp) | Có | Dùng để tính hệ số hiệu quả ở biểu đồ 4.7 |
| Ảnh công trình | Tệp ảnh | Không | Hiển thị trên thẻ dự án |

### Bước 3 — Giá điện (có thể bỏ qua)

- Giá mua điện: chọn Bậc thang EVN sinh hoạt (mặc định) hoặc Giá cố định
  với ô nhập đ/kWh.

- Giá bán lên lưới: ô nhập đ/kWh, mặc định 0 vì hệ dân dụng hiện chưa
  bán điện.

- Giá trị ở bước này nuôi chỉ số Tiền điện tiết kiệm ở mục 4.8.

### Quy tắc

- Serial đã thuộc tài khoản khác: báo lỗi "Thiết bị đang thuộc một tài
  khoản khác" kèm nút Liên hệ hỗ trợ mở màn hình 8.

- Thiết bị hợp lệ nhưng chưa lên mạng: vẫn tạo dự án, thẻ dự án hiện
  chip Đang chờ kết nối cho tới gói dữ liệu đầu tiên.

- Giới hạn số dự án áp dụng như nhau cho mọi tài khoản, mặc định 10 dự
  án, nâng theo yêu cầu ở phía quản trị. Không phân biệt chủ hộ và nhà
  phân phối ở mục này.

## 15. Thông tin tài khoản

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Xem và sửa hồ sơ cá nhân, đổi mật khẩu, yêu cầu xoá tài khoản. |
| Thanh thẻ | Không |
| Điều hướng | Tôi (3) → dòng Thông tin tài khoản → Thông tin tài khoản (15) |

### Thành phần

| **Mục** | **Thao tác** | **Quy tắc** |
|----|----|----|
| Ảnh đại diện | Bấm để đổi | Chụp mới hoặc chọn từ thư viện; nếu trống thì hiển thị chữ viết tắt của họ tên |
| Họ và tên | Sửa trực tiếp | Không để trống |
| Email | Bấm để đổi | Đổi email phải xác thực OTP gửi tới địa chỉ mới |
| Số điện thoại | Bấm để đổi | Đổi số phải xác thực OTP gửi tới số mới |
| Đổi mật khẩu | Mở biểu mẫu | Nhập mật khẩu hiện tại, mật khẩu mới, nhập lại |
| Ngôn ngữ | Mở màn hình 19 | Lưu vào hồ sơ để đồng bộ giữa các thiết bị |
| Xoá tài khoản | Mở hộp xác nhận | Cảnh báo mất toàn bộ dữ liệu; yêu cầu nhập mật khẩu để xác nhận |

### Quy tắc

- Nút Lưu ở cuối màn hình, chỉ bật khi có thay đổi chưa lưu.

- Rời màn hình khi còn thay đổi chưa lưu thì hỏi xác nhận huỷ bỏ.

- Yêu cầu xoá tài khoản không xoá ngay: tạo phiếu, khoá đăng nhập và xoá
  dữ liệu sau 30 ngày.

## 16. Cấu hình dự án

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Xem và thay đổi cách hệ thống vận hành: chế độ, ngưỡng SOC, lịch sạc/xả, quan hệ với lưới điện. |
| Thanh thẻ | Không |
| Điều hướng | Màn hình chính (4) → nút chế độ hoạt động → Cấu hình dự án (16) |

Cấu trúc màn hình tham chiếu ứng dụng mySigen của Sigenergy, là ứng dụng
thương mại có cách tổ chức cấu hình đầy đủ và dễ hiểu cho người dùng dân
dụng. Mỗi tham số ở đây đều đối chiếu với một thanh ghi ghi được (RW)
trong bảng thanh ghi SRNE V2.11, để chắc chắn là thực thi được trên phần
cứng đang dùng.

### 16.1 Phân quyền

| **Cấp** | **Ai** | **Được đổi gì** |
|----|----|----|
| A — Chủ hộ | Chủ tài khoản của dự án | Chế độ vận hành, SOC dự phòng, lịch sạc/xả, thông báo |
| B — Nhà phân phối | Đơn vị lắp đặt | Toàn bộ cấp A, thêm giới hạn dòng sạc và cấu hình lưới điện (cấu hình cấp B). Ngoài nhóm này, giao diện giống hệt chủ hộ. |
| C — Kỹ thuật GG Power | Phòng EMS | Toàn bộ, thêm cấu hình BMS, song song, điện áp/tần số đầu ra |

- Phân quyền theo vai trò của tài khoản, không dùng mật khẩu kỹ thuật
  dùng chung. Backend cấp vai trò; app chỉ hiển thị những nhóm cấu hình
  thuộc vai trò đó. Cách này truy vết được ai đổi gì và không thể chia
  sẻ mật khẩu ra ngoài. Thanh ghi E202 PassWordSet vì vậy không dùng ở
  tầng ứng dụng.

### 16.2 Chế độ vận hành — bỏ Dự phòng mất điện (v0.6)

mySigen có bốn chế độ: Sigen AI, Maximum Self-Consumption, Fully Fed to
Grid và Time-Based Control. Ánh xạ sang phần cứng SRNE như sau.

| **Chế độ (GGP)** | **Hành vi** | **Thanh ghi ghi xuống** |
|----|----|----|
| Tự tiêu thụ tối đa | PV ưu tiên cấp tải, dư thì sạc pin, dư nữa mới lên lưới. Mặc định. | E039 PvPowerPrioritySet = 0; E20F ChgSourcePriority = 0 |
| Ưu tiên sạc pin | PV sạc đầy pin trước rồi mới cấp tải. Dùng mùa mưa hoặc trước bão. | E039 = 1 |
| Phát tối đa lên lưới | PV ưu tiên phát lên lưới. Chỉ dùng khi có hợp đồng mua điện. | E039 = 2; E037 InvToGridEn = 1 |
| Theo khung giờ (TOU) | Sạc/xả theo lịch người dùng đặt ở mục 16.4. | E02C OnTimeChargeEn = 1; E033 OnTimeDischgEn = 1 (tự ghi, xem 16.4a). Rời TOU ghi cả hai về 0 |
| GG AI (pha 2) | EMS cloud tự tối ưu theo giá điện, dự báo bức xạ và thói quen tiêu thụ; kết quả ghi xuống dưới dạng lịch TOU. | EMS tính, ghi qua nhóm thanh ghi TOU |

- Mỗi chế độ hiển thị một thẻ có tên, một dòng mô tả và một sơ đồ nhỏ
  minh hoạ hướng dòng năng lượng; thẻ đang chọn có viền xanh GG.

### 16.3 Pin và ngưỡng SOC

| **Tham số** | **Dải** | **Mặc định** | **Thanh ghi** |
|----|----|----|----|
| SOC dự phòng (cắt xả) | 5 – 100 % | 20 % | E00F BatStopSOC |
| SOC dừng sạc | 50 – 100 % | 100 % | E01D StopChgSocSet |
| Ngưỡng cảnh báo SOC thấp | 5 – 50 % | 15 % | E01E BatSocLowAlarm |
| SOC chuyển sang lưới | 5 – 50 % | 10 % | E01F BatSocSwToLine |
| Dòng sạc tối đa | 0 – 200 A | 120 A | E20A MaxChgCurr (bước 0,1 A) |
| Dòng sạc tối đa từ lưới | 0 – 400 A | 60 A | E205 IbattLineChgLimit |
| Chế độ giới hạn dòng sạc | 0 / 1 / 2 | 1 | E025 BMSChgLCMode |

- Khi có truyền thông BMS, giá trị E20A bị ghi đè bởi giới hạn dòng của
  BMS; người dùng chỉ đặt được giá trị nhỏ hơn giới hạn đó. App phải
  hiện chú thích này ngay dưới thanh trượt.

- SOC dự phòng dùng chung thanh trượt kiểu Backup Reserve của mySigen:
  thanh ngang 0–100 % có vạch mốc 20 % và 50 %, bên dưới ghi số giờ dự
  phòng ước tính theo tải trung bình 7 ngày gần nhất.

### 16.4 Lịch sạc và xả theo khung giờ — cập nhật: chiến lược 16a/16b (v0.6)

Từ v0.5, lịch nằm ở màn con 16a Lịch theo khung giờ, mở bằng nút Chỉnh
sửa lịch trên thẻ chế độ TOU ở màn 16.

Ba khung sạc và ba khung xả độc lập. Mỗi khung có giờ bắt đầu, giờ kết
thúc, SOC dừng, công suất tối đa và nguồn sạc.

| **Trường** | **Thanh ghi** | **Ghi chú** |
|----|----|----|
| Bật lịch sạc | E02C OnTimeChargeEn | 0 tắt / 1 bật. Không hiển thị cho người dùng; tự ghi theo chế độ (16.4a) |
| Khung sạc 1 / 2 / 3 | E026–E02B | Cặp bắt đầu – kết thúc cho từng khung |
| SOC dừng sạc từng khung | E03B / E03C / E03D | 0 – 100 % |
| Công suất sạc tối đa từng khung | E04A / E04B / E04C | 0 – 12 000 W |
| Nguồn sạc từng khung | E04D TimedChgSource | Trường bit: lưới và máy phát cho từng khung |
| Bật lịch xả | E033 OnTimeDischgEn | 0 tắt / 1 bật. Không hiển thị cho người dùng; tự ghi theo chế độ (16.4a) |
| Khung xả 1 / 2 / 3 | E02D–E032 | Cặp bắt đầu – kết thúc cho từng khung |
| SOC dừng xả từng khung | E03E / E03F / E040 | 0 – 100 % |
| Công suất xả tối đa từng khung | E047 / E048 / E049 | 0 – 12 000 W |
| Ngày trong tuần áp dụng | E225 WeekToOnTimeEn | Trường bit 7 ngày, bit00 là Thứ Hai |

- Mã hoá thời gian trong thanh ghi là giờ × 256 + phút. Ví dụ 23:59
  tương ứng giá trị 5947. App gửi giờ và phút riêng, backend quy đổi;
  app không tự tính.

- Phần hiển thị là một dải 24 giờ nằm ngang: đoạn sạc tô cam, đoạn xả tô
  xanh lá, phần còn lại xám. Chạm vào một đoạn để mở biểu mẫu sửa khung
  đó.

- Các khung không được chồng lấn. Khi người dùng đặt chồng, tô đỏ đoạn
  giao nhau và khoá nút Lưu.

### 16.4a Tự bật/tắt lịch theo chế độ vận hành — mới v0.5

Màn 16a không có công tắc bật lịch sạc và bật lịch xả. Hai thanh ghi
E02C OnTimeChargeEn và E033 OnTimeDischgEn do app và backend tự ghi, gắn
với việc chọn chế độ ở mục 16.2. Quy tắc:

- **Kích hoạt TOU:** khi người dùng xác nhận chuyển sang TOU, backend
  ghi toàn bộ lịch trước (khung giờ, SOC dừng, công suất, nguồn sạc,
  E225 ngày áp dụng), đọc lại để xác nhận, sau đó mới ghi E02C = 1 và
  E033 = 1. Thứ tự này tránh để inverter chạy theo một lịch ghi dở.

- **Theo số khung:** E02C = 1 chỉ khi có ít nhất một khung sạc đã đặt;
  E033 = 1 chỉ khi có ít nhất một khung xả đã đặt. Loại khung nào chưa
  có khung nào thì ghi 0.

- **Rời TOU:** khi người dùng chọn chế độ khác, ghi E02C = 0 và E033 = 0
  trong cùng lượt ghi với thanh ghi của chế độ mới. Các khung giờ vẫn
  giữ nguyên trong inverter để lần sau bật lại TOU không phải nhập lại.

- **GG AI:** chế độ GG AI (pha 2) cũng ghi xuống qua nhóm thanh ghi TOU
  nên áp dụng cùng quy tắc bật/tắt.

- **Tạm ngưng theo ngày:** người dùng muốn tạm ngưng lịch vào một số
  ngày thì bỏ chọn các ngày đó (E225), không cần tắt cả lịch. Không cho
  phép lưu khi không có ngày nào được chọn.

- **Đồng bộ:** backend đọc lại E02C và E033 định kỳ. Nếu lệch với chế độ
  đang lưu (ví dụ có người đổi trên màn LCD của inverter), app hiển thị
  chế độ theo giá trị thực đọc được, ghi nhật ký thay đổi và báo cho chủ
  dự án.

### 16.5 Lưới điện

| **Tham số** | **Giá trị** | **Thanh ghi** |
|----|----|----|
| Chế độ làm việc với lưới | 0 độc lập · 1 nối lưới · 2 chống phát ngược tại tải UPS · 3 chống phát ngược tại đầu vào · 4 ghép AC | E037 InvToGridEn |
| Công suất sai số chống phát ngược | 0 – 500 W, mặc định 20 W | E42C ZeroExportPower |
| Giới hạn công suất đỉnh lấy từ lưới | Bước 50 W | E21D MaxLinePower |
| Điện áp đầu ra | 100 – 264 V, mặc định 220 V — cấp C | E208 OutputVoltSet |
| Tần số đầu ra | 45 – 65 Hz, mặc định 50 Hz — cấp C | E209 OutputFreqSet |

- Đã chốt: mặc định xuất xưởng là bật chống phát ngược (E037 InvToGridEn
  = 2 hoặc 3, E42C ZeroExportPower = 20 W). Nhóm tham số này thuộc cấp
  B, chủ hộ không đổi được. Nhà phân phối chỉ mở khi công trình có hợp
  đồng mua điện với đơn vị điện lực.

### 16.6 Bảo vệ và khởi động lại

| **Tham số** | **Thanh ghi** | **Mặc định** |
|----|----|----|
| Tự khởi động lại sau quá tải | E20D AutoRestartOvLoad | Bật |
| Tự khởi động lại sau quá nhiệt | E20E AutoRestartOvTemper | Bật |
| Dừng khi mất truyền thông BMS | E214 BmsErrStopEnable | Tắt |
| Bật còi cảnh báo | E210 AlarmEnable | Bật |
| Chế độ tiết kiệm điện | E20C PowerSavingMode | Tắt |
| Bật / tắt inverter | DF00 CmdPowerOnOff | Chỉ cấp C, có hộp xác nhận hai bước |

### 16.7 Thiết bị và truyền thông — chỉ cấp C

- Truyền thông BMS: E215 BmsCommEnable — 0 không truyền thông, 1 RS485,
  2 CAN.

- Giao thức BMS: E21B Rs485BmsProtocol, tra theo tài liệu "SRNE BMS
  Communication Protocol Definition".

- Địa chỉ Modbus của inverter: E200 Rs485AddrSet, dải 1–254.

- Chế độ song song: E201 ParallMode — 0 độc lập, 1 song song một pha,
  5–7 ba pha.

### 16.8 Quy tắc chung của màn hình

- Mọi thay đổi đều đi qua hộp xác nhận tóm tắt dạng "giá trị cũ → giá
  trị mới", không lưu ngầm.

- Giao diện chỉ cập nhật sau khi ghi thành công. Backend đọc lại (mã
  hàm 03) sau 3 giây để xác nhận; nếu lệch thì hoàn về giá trị cũ và báo
  lỗi.

- Ghi nhật ký mọi thay đổi: người thực hiện, tham số, giá trị cũ, giá
  trị mới, thời điểm. Hiển thị ở mục Lịch sử thay đổi cuối màn hình, giữ
  90 ngày.

- Khi gateway mất kết nối quá 60 giây, khoá toàn bộ thao tác ghi và hiện
  dải cảnh báo "Mất kết nối với hệ thống — không thể thay đổi cấu hình".

- Đã chốt cho bản demo ngày 12/10/2026: màn hình ở chế độ chỉ xem nhưng
  đọc và hiển thị giá trị cấu hình THẬT từ inverter qua mã hàm 03. Các
  công tắc và thanh trượt hiện đúng giá trị hiện hành nhưng bị vô hiệu,
  kèm chú thích "Bản demo — chỉ xem". Tuyệt đối không gửi mã hàm 06 hoặc
  16 xuống inverter thật.

### 16.9 Khác biệt so với mySigen cần chốt

- mySigen có chế độ Sigen AI tự học theo giá điện và dự báo thời tiết.
  GGP pha 1 chưa có EMS cloud đủ dữ liệu, đề xuất lùi sang pha 2.

- mySigen cấu hình cả bộ sạc xe điện và quản lý thẻ IC. Ngoài phạm vi dự
  án này.

- mySigen cho đặt Backup Reserve theo vùng địa lý và quy định lưới địa
  phương. GGP chỉ cần một bộ giá trị cho Việt Nam.

## 17. Theo dõi dữ liệu

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Xem dữ liệu vận hành chi tiết của một thiết bị: đủ để kỹ thuật chẩn đoán từ xa mà không phải đến hiện trường. |
| Thanh thẻ | Không |
| Điều hướng | Thiết bị (5) → chạm vào một thẻ thiết bị → Theo dõi dữ liệu (17) |

### 17.1 Đầu trang dùng chung

- Tên thiết bị, số serial, chip trạng thái, thời điểm cập nhật gần nhất
  dạng "cập nhật 3 giây trước".

- Bộ chọn dạng nổi để chuyển giữa các nhóm dữ liệu. Danh sách nhóm thay
  đổi theo loại thiết bị.

- Nút làm mới thủ công ở góc phải, dùng khi đang ở chế độ chu kỳ chậm.

### 17.2 Khi thiết bị là Inverter

Bốn nhóm: Tổng quan · PV · Lưới và tải · Nhiệt độ và trạng thái.

| **Trường hiển thị** | **Đơn vị** | **Thanh ghi** |
|----|----|----|
| Trạng thái máy | — | 0x210 MachineState (giải mã thành chuỗi tiếng Việt) |
| PV1: điện áp · dòng · công suất | V · A · W | 0x107 · 0x108 · 0x109 |
| PV2: điện áp · dòng · công suất | V · A · W | 0x10F · 0x110 · 0x111 |
| Điện áp lưới | V | 0x213 GridVoltA |
| Dòng lưới | A | 0x214 GridCurrA |
| Tần số lưới | Hz | 0x215 GridFreq (bước 0,01) |
| Công suất trao đổi với lưới | W | 0x23A GridActivePowerA (dương là phát lên lưới) |
| Điện áp · dòng đầu ra inverter | V · A | 0x216 · 0x217 |
| Công suất tải UPS | W | 0x21B LoadActivePowerA |
| Công suất biểu kiến tải | VA | 0x21C LoadApparentPowerA |
| Tỷ lệ tải | % | 0x21F LoadRatioA |
| Công suất tải thường | W | 0x240 HomeLoadActivePowerA |
| Dòng sạc pin từ lưới | A | 0x21E LineChgCurr |
| Dòng sạc pin từ PV | A | 0x224 Ibuck1 |
| Nhiệt độ tản nhiệt DC-DC | °C | 0x220 Tempera |
| Nhiệt độ tản nhiệt DC-AC | °C | 0x221 Temperb |
| Nhiệt độ biến áp | °C | 0x222 Temperc |
| Nhiệt độ môi trường | °C | 0x223 Temperd |
| Mã lỗi đang hoạt động | — | 0x200 CurrErrReg (4 thanh ghi, 64 bit) |

### 17.3 Khi thiết bị là Pin S16 Pre (BMS)

Đây là màn hình dùng để chẩn đoán pin. Chia làm năm nhóm.

Nhóm A — Tổng quan pin

| **Trường hiển thị** | **Đơn vị** | **Thanh ghi** |
|----|----|----|
| SOC | % | 0x100 BatSoc |
| SOH | % | 0x104 BatSoh |
| Số chu kỳ sạc/xả | lần | 0x10C BatCycles |
| Điện áp pack (inverter đo) | V | 0x101 BatVolt |
| Điện áp pack (BMS báo) | V | 0x112 BatBmsVolt |
| Dòng pack (BMS báo) | A | 0x113 BatBmsCurr (có dấu, dương là sạc) |
| Nhiệt độ pin (BMS báo) | °C | 0x114 BatBmsTemp |
| Nhiệt độ pin (inverter đo) | °C | 0x103 DeviceBatTemper |

- Hiển thị lệch giữa điện áp inverter đo và điện áp BMS báo. Lệch quá 1
  V là dấu hiệu tiếp xúc kém hoặc sai cấu hình, cần làm nổi bằng chip
  vàng.

Nhóm B — Giới hạn công suất do BMS quy định

| **Trường hiển thị**               | **Đơn vị** | **Thanh ghi**               |
|-----------------------------------|------------|-----------------------------|
| Điện áp giới hạn sạc              | V          | 0x115 BatBmsChgLimitVolt    |
| Dòng giới hạn sạc                 | A          | 0x116 BatBmsChgLimitCurr    |
| Dòng giới hạn xả                  | A          | 0x117 BatBmsDchgLimitCurr   |
| Công suất sạc cho phép            | W          | Tính: 0x116 × 0x112         |
| Công suất xả cho phép             | W          | Tính: 0x117 × 0x112         |
| Chế độ giới hạn dòng đang áp dụng | —          | E025 BMSChgLCMode (đọc lại) |

- Hiển thị dạng đồng hồ hai cung: dòng hiện tại đặt trên nền dải giới
  hạn sạc và giới hạn xả. Khi dòng chạm giới hạn thì cung đổi sang cam
  và ghi "Đang bị giới hạn bởi BMS". Đây là cách nhanh nhất để giải
  thích vì sao pin sạc chậm.

Nhóm C — Dữ liệu cell

| **Trường hiển thị**     | **Đơn vị** | **Thanh ghi**           |
|-------------------------|------------|-------------------------|
| Điện áp cell cao nhất   | mV         | 0x132 BmsMaxCellVolt    |
| Điện áp cell thấp nhất  | mV         | 0x133 BmsMinCellVolt    |
| Số hiệu cell cao nhất   | —          | 0x136 BmsMaxCellVoltIdx |
| Số hiệu cell thấp nhất  | —          | 0x137 BmsMinCellVoltIdx |
| Chênh áp cell           | mV         | Tính: 0x132 − 0x133     |
| Nhiệt độ cell cao nhất  | °C         | 0x134 BmsMaxCellTemp    |
| Nhiệt độ cell thấp nhất | °C         | 0x135 BmsMinCellTemp    |

- Bảng thanh ghi SRNE V2.11 không có điện áp của từng cell riêng lẻ. Chỉ
  có cell cao nhất, cell thấp nhất và số hiệu của hai cell đó. Đã chốt
  hướng xử lý như sau.

  - Pha 1 (demo 12/10): chỉ hiển thị cell cao nhất, cell thấp nhất,
    chênh áp và số hiệu hai cell đó, đúng phạm vi thanh ghi SRNE đang
    có.

  - Song song: xin nhà sản xuất pack tài liệu giao thức BMS của S16 Pre
    để gateway đọc thẳng BMS qua RS485 hoặc CAN, độc lập với đường đọc
    inverter. Đây là con đường duy nhất chắc chắn lấy được điện áp từng
    cell.

  - Pha 2: khi đường đọc thẳng BMS chạy được thì bổ sung biểu đồ điện áp
    từng cell vào chính nhóm C này, không đổi cấu trúc màn hình.

- Việc cần làm ngay: gửi yêu cầu tài liệu giao thức BMS tới nhà sản xuất
  pack, vì thời gian phản hồi quyết định biểu đồ từng cell có kịp pha 2
  hay không.

- Khi có dữ liệu từng cell, biểu đồ là cột dọc theo số hiệu cell, trục
  tung thu hẹp quanh dải thực tế (ví dụ 3 200 – 3 400 mV) để thấy chênh
  lệch; cột cao nhất tô cam, thấp nhất tô xanh dương, còn lại xám.

Nhóm D — Trạng thái, cảnh báo và bảo vệ

| **Trường hiển thị**     | **Kiểu** | **Thanh ghi**                         |
|-------------------------|----------|---------------------------------------|
| Cờ cảnh báo BMS         | 32 bit   | 0x118 BmsAlarmH · 0x119 BmsAlarmL     |
| Cờ bảo vệ BMS           | 32 bit   | 0x11A BmsProtectH · 0x11B BmsProtectL |
| Bảng lỗi hệ thống BMS   | 16 bit   | 0x131 BmsSysFaultTbl                  |
| Số pack đang trực tuyến | số       | 0x12A BmsModuleNum                    |
| Số pack bị cấm sạc      | số       | 0x12C BmsStopChgPkgCnt                |
| Số pack bị cấm xả       | số       | 0x12D BmsStopDchgPkgCnt               |
| Số pack mất kết nối     | số       | 0x12E BmsOfflinePkgCnt                |

- Backend giải mã từng bit thành tên tiếng Việt (quá áp cell, thấp áp
  cell, quá nhiệt sạc, quá dòng xả, mất cân bằng...). App chỉ hiển thị
  danh sách đã giải mã, không tự giải mã bit.

- Cờ đang bật hiện chip màu theo mức độ: bảo vệ là đỏ, cảnh báo là vàng.
  Cờ tắt thu gọn vào mục "Xem tất cả trạng thái".

- Đã chốt: xin SRNE bảng ánh xạ bit chính thức cho 32 bit cảnh báo và 32
  bit bảo vệ, rồi dịch sang tiếng Việt và bổ sung vào
  docs/02-register-map.md trước khi code nhóm này. Nếu SRNE chưa phản
  hồi kịp, bản demo hiển thị nhãn thô dạng "Cờ bảo vệ bit 3" và thay
  bằng tên tiếng Việt sau, không chặn tiến độ.

Nhóm E — Định danh và phiên bản

| **Trường hiển thị** | **Kiểu** | **Thanh ghi** |
|----|----|----|
| Hãng sản xuất BMS | chuỗi | 0x13A BmsFacturerName (8 thanh ghi) |
| Phiên bản phần mềm BMS | số | 0x12F BmsSwVer |
| Phiên bản phần cứng BMS | số | 0x130 BmsHwVer |
| Phiên bản giao thức truyền thông | số | 0x1C RS485Version |

### 17.4 Biểu đồ lịch sử

- Bộ chọn khoảng: 1 giờ · 24 giờ · 7 ngày.

- Người dùng chọn tối đa 3 trường để vẽ chồng lên nhau; mỗi trường một
  trục hoặc chung trục tuỳ đơn vị.

- Chu kỳ dữ liệu: thời gian thực 5 giây qua WebSocket; lưu vào bảng
  telemetry 1 phút một điểm; khoảng 7 ngày đọc từ continuous aggregate.

- Chạm giữ trên biểu đồ hiện đường dóng và bảng giá trị tại thời điểm
  đó.

### 17.5 Quy tắc

- Chỉ làm mới khi màn hình đang hiển thị. Vào nền thì ngắt WebSocket để
  tiết kiệm pin.

- Trường chưa có dữ liệu hiển thị dấu "—", tuyệt đối không hiển thị 0,
  vì 0 là giá trị hợp lệ của nhiều trường.

- Giá trị nằm ngoài dải hợp lệ của thanh ghi thì hiển thị "—" và ghi log
  phía backend để phát hiện lỗi giải mã.

- Mọi giá trị đã quy đổi sang đơn vị SI ở backend; app không nhân hệ số,
  đúng theo quy ước dữ liệu của dự án.

- Bố cục mỗi nhóm là danh sách hai cột: tên trường bên trái, giá trị và
  đơn vị bên phải, chữ số dùng kiểu bảng để thẳng hàng.

## 18. Thêm thiết bị bằng mã QR

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Quét tem QR trên thiết bị để lấy số serial, thay cho việc nhập tay. |
| Thanh thẻ | Không |
| Điều hướng | Gọi từ Đăng nhập (1) và từ bước 1 của Thêm dự án mới (14); đóng lại là quay về nơi gọi |

### Thành phần

- Khung ngắm vuông ở giữa màn hình, phần ngoài làm tối; hướng dẫn "Đưa
  mã QR trên tem thiết bị vào khung".

- Nút bật đèn pin ở góc dưới bên trái, nút Nhập số serial bằng tay ở
  dưới cùng.

- Quét thành công thì rung nhẹ, hiện thẻ xác nhận model và serial, nút
  Dùng thiết bị này.

### Quy tắc

- Xin quyền camera ngay khi mở; bị từ chối thì hiện màn hình hướng dẫn
  mở lại quyền trong Cài đặt kèm nút Nhập tay.

- Đã chốt: Phòng EMS ra quy định định dạng, nhà máy in tem theo quy định
  đó.

  - Định dạng đề xuất: GGP:\<mã model\>:\<số serial\>:\<checksum\>, phân
    tách bằng dấu hai chấm, toàn bộ viết hoa, không dấu cách. Ví dụ:
    GGP:HYB5K:SN2609A00137:7F.

  - Tiền tố GGP cho phép app loại ngay mã QR không phải của GG Power mà
    không cần gọi mạng.

  - Checksum là hai ký tự hex, tính bằng XOR từng byte của phần đứng
    trước dấu hai chấm cuối; app kiểm tra ngoại tuyến trước khi gọi
    backend.

  - Mã model và dải số serial do nhà máy cấp; Phòng EMS giữ bảng đăng ký
    mã model.

- Mã không đúng định dạng thì hiện thông báo "Mã không hợp lệ" trong 2
  giây rồi tiếp tục quét, không thoát màn hình.

## 19. Chọn ngôn ngữ — đã bỏ (v0.6)

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Đổi ngôn ngữ hiển thị của ứng dụng. |
| Thanh thẻ | Không |
| Điều hướng | Tôi (3) hoặc Thông tin tài khoản (15) → dòng Ngôn ngữ → Chọn ngôn ngữ (19) |

### Thành phần

- Danh sách một cột: Tiếng Việt (mặc định) và English. Mục đang chọn có
  nền xanh nhạt và dấu kiểm.

- Chọn xong áp dụng ngay và quay về màn hình trước, không cần khởi động
  lại ứng dụng.

### Quy tắc

- Lựa chọn lưu vào hồ sơ tài khoản để đồng bộ giữa các thiết bị, không
  chỉ lưu trên máy.

- Lần đầu cài đặt thì lấy theo ngôn ngữ hệ điều hành; không thuộc hai
  ngôn ngữ hỗ trợ thì dùng Tiếng Việt.

- Mọi chuỗi hiển thị đi qua lớp i18n; không viết chuỗi cứng trong mã
  nguồn.

## 20. Kết nối Wi-Fi cho GG SmartBox

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Đưa GG SmartBox lên mạng Wi-Fi của gia đình để gửi dữ liệu về cloud. Không có bước này, dự án mới tạo sẽ ở mãi trạng thái "Đang chờ kết nối". |
| Ưu tiên | P1 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Thêm dự án mới (14) → Tạo dự án → Kết nối Wi-Fi (20) → Màn hình chính (4). Hoặc Thiết bị (5) → thẻ GG SmartBox → Đổi mạng Wi-Fi. |

### Luồng bốn bước

- **Bước 1 — Chuẩn bị thiết bị:** hình minh hoạ nút ghép nối trên GG
  SmartBox, hướng dẫn "Giữ nút 3 giây tới khi đèn nháy xanh". Nút "Đèn
  đã nháy".

- **Bước 2 — Tìm thiết bị:** app tìm GG SmartBox ở gần (Bluetooth, hoặc
  Wi-Fi phát từ thiết bị — xem mục 7). Hiện danh sách thiết bị tìm thấy
  kèm 4 số cuối serial để người dùng chọn đúng máy.

- **Bước 3 — Chọn mạng:** danh sách mạng Wi-Fi mà GG SmartBox quét được,
  có biểu tượng cường độ sóng; ô mật khẩu có nút con mắt. Mạng 5 GHz
  hiện mờ kèm ghi chú "Không hỗ trợ".

- **Bước 4 — Đang kết nối:** danh sách 3 mốc có dấu kiểm lần lượt: Kết
  nối Wi-Fi → Kết nối máy chủ GG Power → Nhận dữ liệu đầu tiên. Thành
  công thì hiện nút "Vào Màn hình chính".

### Lỗi thường gặp và cách hiển thị

| Lỗi | Thông báo cho người dùng | Hành động gợi ý |
|----|----|----|
| Sai mật khẩu Wi-Fi | Mật khẩu Wi-Fi không đúng | Quay lại bước 3, giữ mạng đã chọn |
| Mạng 5 GHz / sóng yếu | GG SmartBox chỉ hỗ trợ Wi-Fi 2,4 GHz, sóng tối thiểu 2 vạch | Chọn mạng khác hoặc đặt bộ phát gần hơn |
| Không tới được máy chủ | Wi-Fi đã kết nối nhưng không ra được Internet | Kiểm tra modem; nút Thử lại |
| Quá thời gian 120 giây | Chưa nhận được dữ liệu từ thiết bị | Thử lại hoặc Liên hệ hỗ trợ (mở màn 8, tự đính kèm serial) |

### Quy tắc

- Mật khẩu Wi-Fi chỉ gửi xuống thiết bị, không lưu trên server và không
  ghi log.

- Nếu người dùng thoát giữa chừng, dự án vẫn được tạo; thẻ dự án hiện
  chip "Đang chờ kết nối" kèm nút "Kết nối ngay".

## 21. Cài đặt dự án

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Sửa thông tin dự án sau khi tạo, cài giá điện đã bỏ qua ở bước 3 màn 14, quản lý thành viên, xoá dự án. |
| Ưu tiên | P1 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Màn hình chính (4) → nút bánh răng ở đầu trang → Cài đặt dự án (21) |

### Thành phần

| Nhóm | Nội dung | Ai được sửa |
|----|----|----|
| Thông tin dự án | Tên, loại công trình, địa chỉ + toạ độ, múi giờ, ngày lắp đặt, công suất PV (kWp), ảnh | Chủ dự án, nhà phân phối |
| Giá điện | Bậc thang EVN hoặc giá cố định; giá bán lên lưới — cùng biểu mẫu với bước 3 màn 14 | Chủ dự án |
| Thành viên | Số người có quyền truy cập, dẫn sang màn 22 | Chủ dự án |
| Thiết bị | Số thiết bị trong dự án, dẫn sang Thiết bị (5) | Chỉ xem |
| Vùng nguy hiểm | Xoá dự án: hộp xác nhận nhập tên dự án; thiết bị được gỡ khỏi tài khoản, dữ liệu lịch sử giữ 30 ngày | Chủ dự án |

### Quy tắc

- Nút Lưu chỉ bật khi có thay đổi; đổi giá điện chỉ áp dụng cho số liệu
  tiết kiệm từ thời điểm lưu, không tính lại quá khứ.

- Đổi địa chỉ thì hỏi có cập nhật toạ độ GPS không, vì toạ độ dùng để
  tra thời tiết và bức xạ.

## 22. Thành viên và phân quyền dự án

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Chủ dự án mời người nhà cùng xem, và cấp hoặc thu hồi quyền của nhà phân phối lắp đặt. |
| Ưu tiên | P1 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Cài đặt dự án (21) → Thành viên → Thành viên và phân quyền (22) |

### Vai trò trong một dự án

| Vai trò | Quyền | Ghi chú |
|----|----|----|
| Chủ dự án | Toàn quyền cấp A; mời/xoá thành viên; xoá dự án | Mỗi dự án đúng một chủ; có thể chuyển quyền sở hữu |
| Thành viên gia đình | Chỉ xem: màn hình chính, thiết bị, cảnh báo, báo cáo | Không vào được Cấu hình dự án |
| Nhà phân phối | Xem toàn bộ + cấu hình cấp B (mục 16.1) | Có thời hạn tuỳ chọn; chủ dự án thu hồi bất cứ lúc nào |

### Thành phần

- Danh sách thành viên: ảnh đại diện, tên, số điện thoại che bớt, chip
  vai trò, ngày tham gia. Chạm vào một người để đổi vai trò hoặc xoá.

- Nút **Mời thành viên**: nhập số điện thoại hoặc email, chọn vai trò,
  gửi lời mời. Lời mời chờ hiển thị riêng với chip "Đang chờ" và nút Huỷ
  lời mời.

- Khối **Nhà phân phối lắp đặt**: tên đơn vị, người phụ trách, hạn
  quyền; nút Thu hồi quyền có hộp xác nhận.

### Quy tắc

- Lời mời hết hạn sau 7 ngày. Người được mời chưa có tài khoản sẽ nhận
  SMS kèm liên kết Đăng ký (13).

- Mọi thay đổi quyền ghi vào nhật ký của dự án, cùng nơi với Lịch sử
  thay đổi ở màn 16.

- Cách nhà phân phối được gán vào dự án lúc lắp đặt: **cần chốt** (mục
  7).

## 23. Chi tiết cảnh báo — đã thiết kế: màn 10a (v0.6)

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Giải thích một cảnh báo cụ thể và hướng dẫn xử lý, để người dùng tự xử lý hoặc gửi phiếu hỗ trợ đúng nội dung. |
| Ưu tiên | P1 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Cảnh báo (10) → chạm vào một mục → Chi tiết cảnh báo (23). Cũng mở được từ thông báo đẩy. |

### Thành phần

- Đầu trang: icon và chip mức độ (Lỗi / Cảnh báo / Thông tin), tiêu đề,
  thiết bị phát sinh, thời điểm bắt đầu, thời điểm kết thúc hoặc "Đang
  diễn ra".

- **Mã lỗi** dạng thô (ví dụ bit trong 0x200 CurrErrReg) và tên tiếng
  Việt do backend giải mã.

- **Nguyên nhân có thể** và **Hướng xử lý**: danh sách bước ngắn do
  Phòng EMS biên soạn theo từng mã lỗi.

- Biểu đồ nhỏ các thông số liên quan trong 30 phút quanh thời điểm cảnh
  báo (ví dụ điện áp lưới khi mất lưới).

- Nút **Tạo phiếu hỗ trợ**: mở màn 8 với loại Kỹ thuật, dự án, tiêu đề
  và mã lỗi điền sẵn. Nút phụ **Đánh dấu đã xử lý**.

### Quy tắc

- Mở chi tiết thì cảnh báo được tính là đã đọc; chấm đỏ trên nút chuông
  chỉ tắt khi không còn cảnh báo chưa đọc.

- Cảnh báo tự hết (ví dụ lưới có lại) chuyển sang Đã xử lý tự động, ghi
  rõ "Tự phục hồi lúc …".

## 24. Gửi yêu cầu thành công

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Xác nhận phiếu đã gửi và cho người dùng mã phiếu để tra cứu. |
| Ưu tiên | P1 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Yêu cầu hỗ trợ (8) → Gửi yêu cầu → Gửi thành công (24) → thẻ Yêu cầu của tôi, phiếu mới nằm trên cùng |

### Thành phần

- Icon dấu kiểm lớn, tiêu đề "Đã gửi yêu cầu", mã phiếu (ví dụ
  \#HT-2909-0151) có nút sao chép.

- Thời gian phản hồi dự kiến theo loại yêu cầu (ví dụ Kỹ thuật: trong 4
  giờ làm việc) và kênh sẽ liên hệ.

- Nút chính **Xem yêu cầu của tôi**; nút phụ **Về Dịch vụ**.

- Gửi lỗi (mất mạng): giữ nguyên nội dung đã nhập, hiện thông báo lỗi và
  nút Gửi lại; không mất tệp đính kèm.

## 25. Đổi email / Đổi số điện thoại

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Thay email hoặc số điện thoại đăng nhập, có xác thực OTP. |
| Ưu tiên | P2 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Thông tin tài khoản (15) → dòng Email hoặc Số điện thoại → màn 25 → quay lại 15 |

- Bước 1: hiện giá trị hiện tại, ô nhập giá trị mới, nút Gửi mã.

- Bước 2: nhập OTP gửi tới giá trị **mới**, dùng lại thành phần OTP của
  màn 12 (6 ô, đếm ngược 60 giây).

- Thành công: thông báo ngắn, gửi email/SMS báo cho giá trị **cũ** để
  phòng bị chiếm tài khoản.

- Giá trị mới đã thuộc tài khoản khác thì báo lỗi ngay dưới ô nhập.

## 26. Đổi mật khẩu

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Đổi mật khẩu khi đang đăng nhập. |
| Ưu tiên | P2 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Thông tin tài khoản (15) → Đổi mật khẩu → màn 26 → quay lại 15 |

- Ba ô: Mật khẩu hiện tại, Mật khẩu mới (có chỉ báo độ mạnh), Nhập lại.
  Liên kết "Quên mật khẩu hiện tại?" dẫn sang màn 12.

- Cùng quy tắc mật khẩu với màn 12. Đổi thành công thì đăng xuất các
  phiên khác, phiên hiện tại giữ nguyên.

## 27. Thêm thiết bị vào dự án

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Bổ sung thiết bị vào dự án đang có, ví dụ lắp thêm pin S16 Pre hoặc đồng hồ đo thông minh. |
| Ưu tiên | P2 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Thiết bị (5) → nút quét QR ở góc phải → màn 27 → Thiết bị (5) |

- Chọn loại thiết bị: Pin lưu trữ · Đồng hồ đo · Inverter song song. Mỗi
  loại có hình minh hoạ vị trí tem QR.

- Định danh bằng Quét mã QR (18) hoặc nhập serial, nút Kiểm tra — cùng
  cách với bước 1 màn 14.

- Với pin: sau khi thêm, hiển thị nhắc "Kiểm tra truyền thông BMS" và
  dẫn sang Theo dõi dữ liệu (17) của pin vừa thêm.

- Thêm inverter song song chỉ dành cho cấp B và C; chủ dự án thấy mục
  này mờ kèm ghi chú "Liên hệ đơn vị lắp đặt".

## 28. Cài đặt thông báo cảnh báo

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Chọn loại cảnh báo được đẩy về điện thoại cho dự án đang mở. |
| Ưu tiên | P2 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Cảnh báo (10) → nút cài đặt → màn 28 |

| Mục | Mô tả | Mặc định |
|----|----|----|
| Lỗi | Mất lưới, lỗi thiết bị, mất kết nối trên 10 phút | Bật, không tắt được |
| Cảnh báo | Nhiệt độ cao, SOC thấp, PV thấp | Bật |
| Thông tin | Firmware mới, hoàn tất cập nhật | Bật |
| Giờ im lặng | Không rung/chuông trong khung giờ đặt; lỗi vẫn báo | Tắt · 22:00–06:00 |
| Ngưỡng SOC thấp | Báo khi SOC dưới ngưỡng | 15 % |

- Thiết lập theo từng dự án; công tắc tổng "Cảnh báo thiết bị" ở thẻ
  Tôi (3) vẫn là công tắc chính cho toàn tài khoản.

## 29. Chi tiết nội dung Khám phá

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Ba mẫu trang chi tiết cho nội dung ở Khám phá (7): sản phẩm, bài viết, video. |
| Ưu tiên | P2 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Khám phá (7) → chạm vào một mục → màn 29 → quay lại 7 |

### 29.1 Chi tiết sản phẩm

- Ảnh sản phẩm dạng trượt, tên, mô tả ngắn, bảng thông số chính (dung
  lượng, công suất, bảo hành), tài liệu PDF tải về.

- Nút chính **Nhận tư vấn**: mở màn 8 với loại Khác và tên sản phẩm điền
  sẵn. Chưa bán hàng trong app ở pha 1.

### 29.2 Chi tiết bài viết

- Ảnh bìa, tiêu đề, chuyên mục, ngày đăng, thời gian đọc; nội dung dạng
  văn bản có ảnh minh hoạ; nút chia sẻ.

### 29.3 Trình phát video

- Khung video 16:9 ở đầu, tiêu đề, sản phẩm liên quan, mô tả; danh sách
  "Video tiếp theo" bên dưới. Nội dung lấy từ CMS (mục 6).

## 30. Sửa khung sạc/xả và xác nhận thay đổi — thay bằng popup trong 16b (v0.6)

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Biểu mẫu sửa một khung trong lịch TOU (16.4) và hộp xác nhận dùng chung cho mọi thay đổi cấu hình (16.8). |
| Ưu tiên | P2 — sau bản demo chỉ xem ngày 12/10/2026 · mới v0.4 |
| Thanh thẻ | Không (bảng trượt từ dưới lên) |
| Điều hướng | Cấu hình dự án (16) → Chỉnh sửa lịch → Lịch theo khung giờ (16a) → chạm một khung → màn 30 |

- Trường: loại khung (Sạc/Xả), giờ bắt đầu, giờ kết thúc, SOC dừng, công
  suất tối đa (W), nguồn sạc (Lưới/Máy phát, chỉ khung sạc). Nút Xoá
  khung.

- Khung chồng lấn với khung khác: tô đỏ đoạn giao nhau trên dải 24 giờ
  thu nhỏ ở đầu bảng và khoá nút Lưu.

- Bấm Lưu mở **hộp xác nhận** liệt kê từng dòng "tham số: giá trị cũ →
  giá trị mới"; xác nhận xong hiện trạng thái Đang ghi…, rồi Đã áp dụng
  hoặc Hoàn về giá trị cũ nếu đọc lại lệch.

## 31. Cập nhật firmware

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Xem phiên bản firmware của từng thiết bị, nội dung thay đổi và tiến trình cập nhật. |
| Ưu tiên | P3 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Thiết bị (5) → dòng Firmware trên thẻ thiết bị, hoặc Chi tiết cảnh báo (23) "Có firmware mới" → màn 31 |

- Mỗi thiết bị một dòng: phiên bản hiện tại, phiên bản mới (nếu có),
  chip Mới nhất / Có bản cập nhật / Đang cập nhật.

- Chi tiết bản mới: ngày phát hành, danh sách thay đổi, thời gian dự
  kiến, lưu ý "Hệ thống có thể mất điện dự phòng trong 3–5 phút".

- Hai lựa chọn: **Cập nhật tự động lúc 02:00** (mặc định) hoặc **Cập
  nhật ngay** (chỉ khi SOC ≥ 30 % và không mất lưới).

- Tiến trình: Tải xuống → Cài đặt → Khởi động lại → Hoàn tất, có phần
  trăm; lỗi thì tự quay về phiên bản cũ và tạo cảnh báo Thông tin.

- Chính sách phát hành và ai được bấm Cập nhật ngay: **cần chốt** (mục
  7).

## 32. Báo cáo

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Tổng hợp sản lượng, tiêu thụ, tiết kiệm theo tháng/năm; xuất file để lưu hoặc gửi. |
| Ưu tiên | P3 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Màn hình chính (4) → nút "Xem báo cáo" dưới mục Chỉ số tổng hợp → màn 32 |

- Bộ chọn Tháng / Năm và điều hướng kỳ; thẻ tổng: sản lượng PV, tiêu
  thụ, tỷ lệ tự dùng, tiền tiết kiệm, giảm CO₂.

- **So sánh kỳ:** cột ghép kỳ này và kỳ trước, kèm phần trăm tăng/giảm
  cho từng chỉ số.

- Bảng theo ngày (hoặc theo tháng khi chọn Năm): PV, tiêu thụ, mua lưới,
  bán lưới, sạc pin, xả pin.

- Nút **Xuất báo cáo**: PDF (trang tóm tắt có biểu đồ) hoặc Excel (số
  liệu thô); tuỳ chọn gửi email tự động đầu mỗi tháng.

## 33. Danh sách dự án cho nhà phân phối

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Giúp nhà phân phối theo dõi hàng chục dự án đã lắp và ưu tiên hỗ trợ dự án đang có vấn đề. |
| Ưu tiên | P3 · mới v0.4 |
| Thanh thẻ | Cấp tài khoản: Dự án · Tôi (giống chủ hộ) |
| Điều hướng | Là màn Dự án (2) khi tài khoản có vai trò nhà phân phối; chọn dự án → Màn hình chính (4) |

- Dải tổng quan ở đầu: Tổng số dự án · Đang lỗi · Offline · Có cảnh báo;
  chạm vào một ô để lọc nhanh.

- Bộ lọc dạng chip: Tất cả / Lỗi / Offline / Cảnh báo / Bình thường; sắp
  xếp theo mức độ nghiêm trọng, rồi theo thời gian cảnh báo gần nhất.

- Thẻ dự án rút gọn một dòng: tên chủ hộ, địa chỉ, chip trạng thái, cảnh
  báo gần nhất; ô tìm kiếm theo tên, số điện thoại chủ hộ hoặc serial.

- Theo quyết định ở mục 8: ngoài danh sách này và nhóm cấu hình cấp B,
  giao diện nhà phân phối giống chủ hộ.

## 34. Trạng thái đặc biệt và lần đầu mở app

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Thống nhất cách hiển thị khi không có dữ liệu, mất kết nối, đang tải, và các bước xin quyền lần đầu. |
| Ưu tiên | P3 · mới v0.4 |
| Thanh thẻ | Theo màn hình chứa trạng thái |
| Điều hướng | Không phải màn độc lập — áp dụng cho mọi màn |

| Trạng thái | Cách hiển thị | Áp dụng |
|----|----|----|
| Đang tải | Khung xương (skeleton) đúng hình các box; không dùng vòng quay toàn màn | Mọi màn có dữ liệu |
| Mất kết nối thiết bị | Dải vàng dưới đầu trang "Mất kết nối lúc 09:12 — dữ liệu có thể chưa cập nhật"; số liệu mờ đi; khoá thao tác ghi | 4, 5, 16, 17 |
| Điện thoại mất mạng | Dải xám "Không có kết nối Internet"; giữ dữ liệu đã tải | Toàn app |
| Không có dữ liệu | Minh hoạ nhỏ + một câu giải thích + hành động (ví dụ "Kết nối ngay") | Biểu đồ, danh sách |
| Lỗi máy chủ | Thông báo ngắn + nút Thử lại | Toàn app |

### Lần đầu mở app

- Màn chào (logo, khẩu hiệu) tối đa 1 giây, rồi vào Đăng nhập (1).

- Xin quyền đúng lúc cần, không xin hết lúc mở app: Thông báo — sau lần
  đăng nhập đầu; Camera — khi mở màn 18; Vị trí — khi bấm nút định vị ở
  màn 14; Bluetooth — khi vào màn 20.

- Mỗi lần xin quyền có một màn giải thích ngắn trước hộp thoại của hệ
  điều hành; bị từ chối thì chỉ đường vào Cài đặt.

## 35. Giới thiệu ứng dụng

| **Thuộc tính** | **Giá trị** |
|----|----|
| Mục đích | Thông tin phiên bản app và các văn bản pháp lý, câu hỏi thường gặp. |
| Ưu tiên | P3 · mới v0.4 |
| Thanh thẻ | Không |
| Điều hướng | Tôi (3) → dòng Giới thiệu ứng dụng (thêm vào nhóm Cài đặt hệ thống) → màn 35 |

- Logo, tên app, phiên bản và số build; nút Kiểm tra cập nhật (mở cửa
  hàng ứng dụng).

- Danh sách liên kết: Điều khoản sử dụng, Chính sách bảo mật, Giấy phép
  mã nguồn mở, Câu hỏi thường gặp, Đánh giá ứng dụng.

- Chạm 7 lần vào số phiên bản mở chế độ chẩn đoán (xem log kết nối) cho
  kỹ thuật GG Power — chỉ vai trò cấp C.

# 4. Thành phần giao diện dùng chung

| **Thành phần** | **Mô tả** |
|----|----|
| Thanh thẻ nổi | Viên thuốc bo tròn, nền trắng, đổ bóng, cách mép 20 dp. Mỗi mục có icon 22 dp trên nhãn 11 dp; mục đang chọn có nền xanh nhạt \#E3F3E8, chữ xanh \#0F7A3A. Nội dung màn hình chừa 108 dp phía dưới để không bị che. |
| Bộ chọn dạng nổi (segmented) | Khung nền \#EAEEE8 bo 12 dp; mục đang chọn nền trắng, đổ bóng nhẹ, chữ đậm. Dùng cho Ngày/Tháng/Năm, Sản phẩm/Tin tức/Hướng dẫn, Loại yêu cầu, °C/°F, Sáng/Tối/Tự động. |
| Thẻ (card) | Nền \#F9FAF7, viền \#E3E8E1, bo 16 dp, bóng nhẹ; màu gần sát nền màn hình \#F5F7F3. |
| Chip trạng thái | Nhãn nhỏ bo 6 dp, có chấm màu: xanh (Online / Còn bảo hành), vàng (Cảnh báo / Sắp hết hạn), đỏ (Lỗi / Hết bảo hành). |
| Công tắc | Bật: nền xanh \#0F7A3A; tắt: nền xám, viền \#7F8B85. |
| Danh sách thả xuống | Ô chọn hiển thị mục hiện tại và mũi tên; danh sách nổi phía dưới, mục đang chọn có dấu ✓. |
| Đầu trang màn hình con | Nút ← (48 dp) + tiêu đề 22 dp; không có thanh thẻ. |

### Màu theo nguồn năng lượng (dùng thống nhất ở mọi biểu đồ)

| **Nguồn**    | **Màu**               | **Ghi chú**             |
|--------------|-----------------------|-------------------------|
| PV           | \#E0700F (cam)        | Sản lượng điện mặt trời |
| Pin          | \#149C4A (xanh lá)    | Sạc (+) / xả (−)        |
| Lưới điện    | \#2F6FB3 (xanh dương) | Mua (+) / bán (−)       |
| Tải tiêu thụ | \#6B7771 (xám)        | Phụ tải trong nhà       |

# 5. Quy ước định dạng dữ liệu

- **Tiếng Việt:** dấu phẩy thập phân, dấu chấm phân cách hàng nghìn (4,2
  kW; 48.000 đ). **Tiếng Anh:** ngược lại (4.2 kW; 48,000 VND).

- Có khoảng trắng giữa số và đơn vị: 76 %, 42 °C, 18,6 kWh.

- Ngày dạng dd/mm/yyyy; thời điểm dạng hh:mm · dd/mm/yyyy.

- Tự đổi đơn vị theo độ lớn: kWh → MWh; kg → t.

- Nhiệt độ hiển thị theo cài đặt °C/°F ở thẻ Tôi.

# 6. Dữ liệu cần từ hệ thống (gợi ý cho backend/EMS)

| **Nhóm** | **Dữ liệu** | **Dùng ở màn hình** |
|----|----|----|
| Thời gian thực | Công suất PV, lưới, pin, tải; SOC; trạng thái sạc/xả; trạng thái kết nối | 2, 4, 5, 11 |
| BMS | Điện áp, dòng, nhiệt độ, SOH, số chu kỳ, chênh áp cell | 4, 5, 11 |
| Lịch sử | Chuỗi công suất theo giờ/ngày/tháng; sản lượng PV tách tự dùng / sạc pin / lên lưới; tiêu thụ | 4 |
| Chỉ số tính toán | Tỷ lệ tự dùng, tiền tiết kiệm (theo biểu giá), giảm CO₂ (theo hệ số phát thải), năng lượng trong pin theo nguồn | 4, 11 |
| Sự kiện | Mã lỗi, mức độ, thời điểm, trạng thái xử lý | 4, 10 |
| Thiết bị | Model, serial, firmware, ngày kích hoạt, gói và hạn bảo hành | 5, 9 |
| Tài khoản | Hồ sơ, danh sách dự án, cài đặt thông báo và hiển thị | 2, 3 |
| Nội dung | Sản phẩm, tin tức, video (CMS) | 7 |
| Hỗ trợ | Tạo phiếu, đính kèm, trạng thái phiếu | 8, 9 |
| Kết nối thiết bị | Trạng thái ghép nối, danh sách Wi-Fi thiết bị quét được, mốc kết nối máy chủ, gói dữ liệu đầu tiên | 20, 34 |
| Thành viên | Vai trò theo dự án, lời mời, hạn quyền nhà phân phối, nhật ký thay đổi quyền | 21, 22, 33 |
| Firmware | Phiên bản theo thiết bị, bản phát hành, nhật ký thay đổi, tiến trình OTA | 31 |
| Báo cáo | Tổng hợp theo ngày/tháng, dịch vụ xuất PDF/Excel, lịch gửi email | 32 |
| Tri thức lỗi | Nguyên nhân và hướng xử lý theo từng mã lỗi (Phòng EMS biên soạn) | 23 |

# 7. Điểm cần xác nhận

1.  Nhà phân phối được thấy thêm gì ngoài nhóm cấu hình cấp B: hiện đã
    chốt là không có khác biệt nào khác, cần xác nhận lại khi có phản
    hồi từ nhà phân phối đầu tiên.

2.  Hotline và email hỗ trợ chính thức của GG Power.

3.  Thông số sản phẩm trong Khám phá (dung lượng S16 Pre, dải công suất
    inverter, công suất tấm pin).

4.  Ngưỡng "Sắp hết hạn" bảo hành (đề xuất 90 ngày) và cách xử lý bảo
    hành theo từng thiết bị.

5.  Phương pháp tính năng lượng trong pin theo nguồn (PV / lưới).

6.  Thiết kế giao diện Tối cho toàn bộ màn hình, kể cả các màn hình mới
    bổ sung từ 12 đến 19.

7.  **Cách nhà phân phối vào dự án của khách (màn 22, 33).** Đề xuất:
    nhà phân phối tạo dự án bằng QR lúc lắp đặt trong tài khoản của
    mình, rồi chuyển quyền sở hữu cho chủ hộ qua số điện thoại; nhà phân
    phối tự động giữ vai trò Nhà phân phối, chủ hộ thu hồi được. Phương
    án khác: chủ hộ tự thêm dự án rồi mời nhà phân phối.

8.  **Phương thức đưa GG SmartBox lên Wi-Fi (màn 20):** Bluetooth (trải
    nghiệm tốt hơn, cần phần cứng hỗ trợ BLE) hay điểm phát Wi-Fi riêng
    của thiết bị (SoftAP). Cần xác nhận khả năng phần cứng của GG
    SmartBox.

9.  **Chính sách cập nhật firmware (màn 31):** ai được bấm Cập nhật ngay
    (chủ hộ hay chỉ cấp B/C), có cho hoãn cập nhật không, và phát hành
    theo đợt hay đồng loạt.

10. **Định dạng và tần suất báo cáo (màn 32):** mẫu PDF có dùng nhận
    diện GG Power không, và có gửi báo cáo tháng qua email mặc định
    không.

# 8. Quyết định đã chốt

Các quyết định dưới đây được chốt ngày 26/09/2026 và đã áp vào nội dung
tài liệu. Mục này giữ lại để đội phát triển biết vì sao tài liệu viết
như vậy.

| **Nội dung** | **Quyết định** | **Mục liên quan** |
|----|----|----|
| Điện áp từng cell pin | Pha 1 hiển thị cell cao nhất / thấp nhất / chênh áp / số hiệu cell. Song song xin tài liệu giao thức BMS của S16 Pre để gateway đọc thẳng; biểu đồ từng cell đưa vào pha 2. | 17.3 nhóm C |
| Chống phát ngược lên lưới | Bật mặc định khi xuất xưởng, thuộc nhóm cấu hình cấp B. Chủ hộ không đổi được. | 16.5 |
| Màn hình Cấu hình dự án ở bản demo | Chế độ chỉ xem, đọc và hiển thị giá trị cấu hình thật từ inverter. Không gửi lệnh ghi. | 16.8 |
| Vị trí danh sách Yêu cầu của tôi | Là thẻ thứ hai ngay trong màn hình Yêu cầu hỗ trợ (8), không tạo màn hình mới. | 8 |
| Phân quyền cấu hình | Theo vai trò của tài khoản, không dùng mật khẩu kỹ thuật dùng chung. Không dùng E202. | 16.1 |
| Chuyển nhanh giữa các dự án | Giữ nguyên cách hiện tại: quay lại danh sách Dự án. Không thêm bộ chọn ở pha 1. | 2, 4 |
| Khác biệt của tài khoản nhà phân phối | Chỉ khác ở quyền cấu hình cấp B tại màn hình 16. Giao diện còn lại giống chủ hộ. | 16.1 |
| Định dạng mã QR trên tem | Phòng EMS ra quy định, nhà máy in theo. Định dạng đề xuất GGP:\<model\>:\<serial\>:\<checksum\>. | 18 |
| Ánh xạ bit cảnh báo và bảo vệ BMS | Xin SRNE bảng bit chính thức. Demo tạm hiển thị nhãn thô nếu chưa có phản hồi. | 17.3 nhóm D |
