# Design tokens · GG Power

Nguồn: design system GG Power (`tokens.json`). Dùng tên token khi viết code, không hard-code mã màu trong màn hình.

## Màu

| Token | Light | Dark | Dùng cho |
|---|---|---|---|
| `gg-orange` | `#f5831f` | `#f5831f` | Brand orange, exact from the logo (the "GG" and the upper swoosh). Identity fills, the logo, solar/PV highlights. Never as text on light grounds (2.6:1 on white); text on it is `on-accent`. |
| `gg-green` | `#149c4a` | `#149c4a` | Brand green, exact from the logo ("POWER", the tagline, the lower swoosh). Identity fills and marks; large text 24px+ only on `surface-raised` (3.6:1). For body-size green text use `primary`. |
| `surface` | `#f5f7f3` | `#0c1612` | Page background. |
| `surface-raised` | `#ffffff` | `#15221d` | Cards, sheets, menus, inputs. |
| `surface-sunken` | `#eaeee8` | `#08100d` | Recessed wells: segmented-control track, slider track, code blocks. |
| `line` | `#d9dfd8` | `#26352f` | Decorative hairlines and card borders (not for controls). |
| `line-strong` | `#7f8b85` | `#6b7a73` | Control borders (inputs, secondary button, unchecked toggle); 3:1 on surface and surface-raised in both themes. |
| `ink` | `#13201b` | `#eef3ef` | Primary text on surface, surface-raised and surface-sunken. |
| `ink-muted` | `#56625c` | `#9fada6` | Secondary text, labels, units, metadata on surface, surface-raised and surface-sunken. |
| `primary` | `#0f7a3a` | `#34c26f` | Primary action fill, selected state, links, and green text on surface / surface-raised / primary-soft. The accessible working shade of gg-green. |
| `primary-hover` | `#0b6630` | `#5bd68c` | Hover/pressed fill for primary. |
| `on-primary` | `#ffffff` | `#0c1612` | Text and icons on a primary fill. |
| `primary-soft` | `#e3f3e8` | `#12301f` | Tinted ground for selected cards and success notes; primary text reads on it. |
| `accent` | `#f5831f` | `#f7963a` | Orange accent fill: solar/PV emphasis, marketing CTA. Text on it is on-accent (dark), never white. |
| `on-accent` | `#13201b` | `#0c1612` | Text and icons on an accent fill. |
| `accent-text` | `#a34b00` | `#f7963a` | Orange text on surface, surface-raised and accent-soft. |
| `accent-soft` | `#fff1e3` | `#3a2410` | Tinted ground for PV / solar highlights; accent-text reads on it. |
| `focus` | `#0f7a3a` | `#5bd68c` | Keyboard focus ring colour (solid 2px, 2px offset); 3:1+ on every surface. |
| `success` | `#0f7a3a` | `#34c26f` | Success / online / charging status text and icons. Pair with a word or icon. |
| `success-soft` | `#e3f3e8` | `#12301f` | Ground behind success text. |
| `warning` | `#8a6100` | `#e0b341` | Warning status text and icons on surface, surface-raised and warning-soft. Pair with a word or icon. |
| `warning-soft` | `#fdf3d6` | `#33280a` | Ground behind warning text. |
| `danger` | `#c62828` | `#ff7a70` | Fault / alarm / destructive status on surface, surface-raised and danger-soft. Pair with a word or icon. |
| `danger-soft` | `#fdecea` | `#3a1513` | Ground behind danger text. |
| `info` | `#2f6fb3` | `#6fa8e8` | Informational status on surface, surface-raised and info-soft. |
| `info-soft` | `#e7f0fa` | `#102436` | Ground behind info text. |
| `energy-pv` | `#e0700f` | `#f7963a` | Chart series and icon for solar PV generation. |
| `energy-battery` | `#149c4a` | `#34c26f` | Chart series and icon for battery (BESS) charge/discharge and SoC. |
| `energy-grid` | `#2f6fb3` | `#6fa8e8` | Chart series and icon for grid import/export. |
| `energy-load` | `#6b7771` | `#9fada6` | Chart series and icon for site load / consumption. |

## Giá trị đang dùng thực tế trên thiết kế (bổ sung)

Một số giá trị trong các màn hình hơi khác token gốc. Khi dựng app, dùng đúng các giá trị dưới đây để khớp ảnh thiết kế:

| Tên đề xuất | Giá trị | Chỗ dùng |
|---|---|---|
| `card-bg` | `#f9faf7` | Nền mọi card/box trên nền `surface` |
| `card-border` | `#e3e8e1` | Viền 1px của card, đường kẻ giữa các dòng trong card |
| `shadow-card` | xem bên dưới | Đổ bóng card |
| `strategy-a` … `strategy-d` | `#0f7a3a`, `#2f6fb3`, `#8a4fbf`, `#b36b00` | Màu chữ A/B/C/D của chiến lược TOU (màn 16a) |
| `tou-charge` | `#e0700f` (= `energy-pv`) | Đoạn sạc trên thanh 24h |
| `tou-discharge` | `#149c4a` (= `energy-battery`) | Đoạn xả trên thanh 24h |
| `sunday-text` | `#c62828` (= `danger`) | Chữ "CN" khi chưa chọn |

## Chữ

Font: **Montserrat** (400/500/600/700). Tải bản TTF từ Google Fonts cho Android (`res/font`). Mono: ui-monospace, "SF Mono", Menlo, Consolas, monospace.

| Style | Size / line-height | Weight | Letter spacing | Dùng cho |
|---|---|---|---|---|
| `display` | 48px / 56px | 700 | -0.02em | Hero headlines, cover and marketing pages. One per view. |
| `heading-1` | 32px / 40px | 700 | -0.01em | Page titles. |
| `heading-2` | 24px / 32px | 600 | 0 | Section titles, screen titles in the app. |
| `heading-3` | 18px / 26px | 600 | 0 | Card titles, dialog titles. |
| `eyebrow` | 12px / 16px | 600 | 0.2em | Uppercase label above a heading; ink-muted or primary. Short phrases only. |
| `body` | 15px / 24px | 400 | 0 | Default running text. |
| `body-sm` | 13px / 20px | 400 | 0 | Descriptions under titles, table cells, helper text. |
| `label` | 13px / 16px | 600 | 0 | Buttons, tabs, form labels. |
| `caption` | 12px / 16px | 500 | 0 | Chart axes, legends, units, timestamps. |
| `metric-lg` | 40px / 44px | 600 | -0.02em | The one headline figure on a dashboard or screen. Use tabular numbers. |
| `metric` | 24px / 28px | 600 | 0 | Stat tiles and KPI rows. Unit in caption style, ink-muted. |
| `code` | 13px / 20px | 400 | 0 | Register maps, device IDs, logs. |

Trên màn hình app: tiêu đề màn 22/28 600, tiêu đề mục (eyebrow) 12/16 600 chữ hoa letter-spacing 0.2em màu `ink-muted`, số liệu lớn dùng `font-variant-numeric: tabular-nums`.

## Khoảng cách

| Token | Giá trị | Dùng cho |
|---|---|---|
| `space-1` | 4px | Icon-to-label gap, tight inline gaps. |
| `space-2` | 8px | Gaps inside controls, between chips. |
| `space-3` | 12px | Gap between stacked form rows, list item padding. |
| `space-4` | 16px | Card padding (mobile), gutters between cards. |
| `space-5` | 20px | Screen side margins on mobile. |
| `space-6` | 24px | Card padding (desktop), gap between sections inside a card. |
| `space-8` | 32px | Gap between page sections. |
| `space-12` | 48px | Hero and page-level vertical rhythm. |

## Bo góc

| Token | Giá trị | Dùng cho |
|---|---|---|
| `radius-sm` | 6px | Badges, chips, small inputs. |
| `radius-md` | 10px | Buttons, inputs, segmented control items. |
| `radius-lg` | 16px | Cards, sheets, mode cards, stat tiles. |
| `radius-pill` | 999px | Toggles, slider tracks, pills. |

## Đổ bóng

| Token | Light | Dark | Dùng cho |
|---|---|---|---|
| `shadow-card` | `0 1px 2px rgba(19,32,27,0.06), 0 2px 8px rgba(19,32,27,0.05)` | `none` | Resting cards on surface. In dark, cards separate by surface-raised + line, no shadow. |
| `shadow-overlay` | `0 8px 24px rgba(19,32,27,0.14)` | `0 8px 24px rgba(0,0,0,0.5)` | Menus, popovers, dialogs, the toggle/slider thumb. |
| `focus-ring` | `0 0 0 2px #ffffff, 0 0 0 4px #0f7a3a` | `0 0 0 2px #0c1612, 0 0 0 4px #5bd68c` | Keyboard focus on every interactive element: a 2px gap then a solid 2px focus ring. |

## Quy ước số và thời gian (tiếng Việt)

- Dấu thập phân là dấu phẩy, phân cách hàng nghìn là dấu chấm: `4,2 kW`, `3.000 W`, `48.000 đ`.
- Có khoảng trắng trước đơn vị: `76 %`, `20 °C`.
- Thời điểm trên biểu đồ: `dd/MM HH:mm` (ví dụ `24/09 09:41`). Khung giờ: `HH:mm – HH:mm` với gạch ngang dài có khoảng trắng.
- Thứ viết tắt: T2 … T7, CN.
