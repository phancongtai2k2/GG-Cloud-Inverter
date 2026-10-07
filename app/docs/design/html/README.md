# Xem prototype HTML trên máy

Các file `*.dc.html` là prototype tương tác (bấm được) của từng màn. `support.js` là runtime để chạy chúng.

```
cd docs/design/html
python3 -m http.server 8000
```

Mở trình duyệt: `http://localhost:8000/Main.dc.html` (thay tên file để xem màn khác). Cần mạng để tải font Montserrat.
Mở trực tiếp bằng `file://` sẽ không chạy — phải qua http server như trên.

Các liên kết giữa màn trong prototype mở file HTML tương ứng. Mỗi màn giữ trạng thái riêng.
