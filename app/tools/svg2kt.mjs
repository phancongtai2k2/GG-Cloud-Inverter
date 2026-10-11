// Chuyển SVG ngôi nhà của Main.dc.html thành mã Kotlin (danh sách lệnh vẽ, toạ độ cuối trong khung 700 × 690).
// Dùng: node svg2kt.mjs <house.html> <out.kt>
import fs from 'node:fs';

const [, , inFile, outFile] = process.argv;
const html = fs.readFileSync(inFile, 'utf8');
const start = html.indexOf('<svg width="700" height="552"');
const end = html.indexOf('</svg>', start);
const svg = html.slice(start, end + 6);

// ---------- Ma trận ----------
const I = [1, 0, 0, 1, 0, 0];
const mul = (m, n) => [
  m[0] * n[0] + m[2] * n[1], m[1] * n[0] + m[3] * n[1],
  m[0] * n[2] + m[2] * n[3], m[1] * n[2] + m[3] * n[3],
  m[0] * n[4] + m[2] * n[5] + m[4], m[1] * n[4] + m[3] * n[5] + m[5],
];
const ap = (m, x, y) => [m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]];
function parseTransform(t) {
  let m = I;
  if (!t) return m;
  const re = /(\w+)\(([^)]*)\)/g;
  let r;
  while ((r = re.exec(t))) {
    const v = r[2].split(/[\s,]+/).filter(Boolean).map(Number);
    let n;
    if (r[1] === 'translate') n = [1, 0, 0, 1, v[0], v[1] || 0];
    else if (r[1] === 'scale') n = [v[0], 0, 0, v[1] ?? v[0], 0, 0];
    else if (r[1] === 'matrix') n = v;
    else if (r[1] === 'rotate') { const a = v[0] * Math.PI / 180; n = [Math.cos(a), Math.sin(a), -Math.sin(a), Math.cos(a), 0, 0]; }
    else throw new Error('transform ' + r[1]);
    m = mul(m, n);
  }
  return m;
}

// viewBox="28 2 270 213" trong 700 × 552, preserveAspectRatio xMidYMid meet
const s = Math.min(700 / 270, 552 / 213);
const ROOT = [s, 0, 0, s, (700 - 270 * s) / 2 - 28 * s, (552 - 213 * s) / 2 - 2 * s];

// ---------- Phân tích thẻ ----------
const tokens = [];
const tagRe = /<(\/?)([a-zA-Z]+)((?:\s+[a-zA-Z0-9:-]+="[^"]*")*)\s*(\/?)>|([^<]+)/g;
let t;
while ((t = tagRe.exec(svg))) {
  if (t[5] !== undefined) { tokens.push({ text: t[5] }); continue; }
  const attrs = {};
  t[3].replace(/([a-zA-Z:-]+)="([^"]*)"/g, (_, k, v) => { attrs[k] = v; });
  tokens.push({ close: !!t[1], name: t[2], attrs, selfClose: !!t[4] });
}

// Cây phần tử
function build() {
  const root = { name: 'root', attrs: {}, children: [] };
  const stack = [root];
  for (const tk of tokens) {
    const top = stack[stack.length - 1];
    if (tk.text !== undefined) { if (tk.text.trim()) top.children.push({ name: '#text', text: tk.text }); continue; }
    if (tk.close) { stack.pop(); continue; }
    const node = { name: tk.name, attrs: tk.attrs, children: [] };
    top.children.push(node);
    if (!tk.selfClose) stack.push(node);
  }
  return root;
}
const tree = build();

// defs: gradient, clipPath, id
const ids = {};
(function index(n) { if (n.attrs && n.attrs.id) ids[n.attrs.id] = n; (n.children || []).forEach(index); })(tree);

// ---------- Hình → đường (toạ độ cục bộ, lệnh M L C Q Z tuyệt đối) ----------
const K = 0.5522847498;
function ellipseCmds(cx, cy, rx, ry) {
  return [
    ['M', cx + rx, cy],
    ['C', cx + rx, cy + K * ry, cx + K * rx, cy + ry, cx, cy + ry],
    ['C', cx - K * rx, cy + ry, cx - rx, cy + K * ry, cx - rx, cy],
    ['C', cx - rx, cy - K * ry, cx - K * rx, cy - ry, cx, cy - ry],
    ['C', cx + K * rx, cy - ry, cx + rx, cy - K * ry, cx + rx, cy],
    ['Z'],
  ];
}
function rectCmds(x, y, w, h, rx, ry) {
  if (!rx && !ry) return [['M', x, y], ['L', x + w, y], ['L', x + w, y + h], ['L', x, y + h], ['Z']];
  rx = Math.min(rx || ry, w / 2); ry = Math.min(ry || rx, h / 2);
  const kx = K * rx, ky = K * ry;
  return [
    ['M', x + rx, y], ['L', x + w - rx, y],
    ['C', x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry], ['L', x + w, y + h - ry],
    ['C', x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h], ['L', x + rx, y + h],
    ['C', x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry], ['L', x, y + ry],
    ['C', x, y + ry - ky, x + rx - kx, y, x + rx, y], ['Z'],
  ];
}
const nums = (str) => str.trim().split(/[\s,]+/).map(Number);
function polyCmds(points, close) {
  const v = nums(points); const out = [];
  for (let i = 0; i < v.length; i += 2) out.push([i ? 'L' : 'M', v[i], v[i + 1]]);
  if (close) out.push(['Z']);
  return out;
}
// Cung elip SVG → bezier bậc ba (SVG spec F.6.5)
function arcToCubics(x1, y1, rx, ry, phi, fa, fs, x2, y2) {
  const rad = phi * Math.PI / 180, cos = Math.cos(rad), sin = Math.sin(rad);
  const dx = (x1 - x2) / 2, dy = (y1 - y2) / 2;
  const x1p = cos * dx + sin * dy, y1p = -sin * dx + cos * dy;
  rx = Math.abs(rx); ry = Math.abs(ry);
  const lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
  if (lam > 1) { rx *= Math.sqrt(lam); ry *= Math.sqrt(lam); }
  const num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
  const den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
  let co = Math.sqrt(Math.max(0, num / den)); if (fa === fs) co = -co;
  const cxp = co * rx * y1p / ry, cyp = -co * ry * x1p / rx;
  const cx = cos * cxp - sin * cyp + (x1 + x2) / 2, cy = sin * cxp + cos * cyp + (y1 + y2) / 2;
  const ang = (ux, uy, vx, vy) => { const a = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy); return a; };
  const th1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
  let dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
  if (!fs && dth > 0) dth -= 2 * Math.PI; else if (fs && dth < 0) dth += 2 * Math.PI;
  const segs = Math.ceil(Math.abs(dth) / (Math.PI / 2));
  const d = dth / segs, k = 4 / 3 * Math.tan(d / 4);
  const pt = (a) => [cx + rx * Math.cos(a) * cos - ry * Math.sin(a) * sin, cy + rx * Math.cos(a) * sin + ry * Math.sin(a) * cos];
  const der = (a) => [-rx * Math.sin(a) * cos - ry * Math.cos(a) * sin, -rx * Math.sin(a) * sin + ry * Math.cos(a) * cos];
  const out = [];
  for (let i = 0; i < segs; i++) {
    const a = th1 + i * d, b = a + d;
    const p0 = pt(a), p3 = pt(b), d0 = der(a), d3 = der(b);
    out.push(['C', p0[0] + k * d0[0], p0[1] + k * d0[1], p3[0] - k * d3[0], p3[1] - k * d3[1], p3[0], p3[1]]);
  }
  return out;
}
function pathCmds(d) {
  const re = /([MLHVCQAZmlhvcqaz])([^MLHVCQAZmlhvcqaz]*)/g;
  const out = []; let r, cx = 0, cy = 0, sx = 0, sy = 0;
  while ((r = re.exec(d))) {
    const c = r[1]; const v = r[2].trim() ? nums(r[2]) : [];
    if (c !== c.toUpperCase() && c !== 'z') throw new Error('relative path cmd ' + c);
    switch (c) {
      case 'M': for (let i = 0; i < v.length; i += 2) { out.push([i ? 'L' : 'M', v[i], v[i + 1]]); cx = v[i]; cy = v[i + 1]; if (!i) { sx = cx; sy = cy; } } break;
      case 'L': for (let i = 0; i < v.length; i += 2) { out.push(['L', v[i], v[i + 1]]); cx = v[i]; cy = v[i + 1]; } break;
      case 'H': for (const x of v) { out.push(['L', x, cy]); cx = x; } break;
      case 'V': for (const y of v) { out.push(['L', cx, y]); cy = y; } break;
      case 'C': for (let i = 0; i < v.length; i += 6) { out.push(['C', ...v.slice(i, i + 6)]); cx = v[i + 4]; cy = v[i + 5]; } break;
      case 'Q': for (let i = 0; i < v.length; i += 4) { out.push(['Q', ...v.slice(i, i + 4)]); cx = v[i + 2]; cy = v[i + 3]; } break;
      case 'A': for (let i = 0; i < v.length; i += 7) { out.push(...arcToCubics(cx, cy, v[i], v[i + 1], v[i + 2], v[i + 3], v[i + 4], v[i + 5], v[i + 6])); cx = v[i + 5]; cy = v[i + 6]; } break;
      case 'Z': case 'z': out.push(['Z']); cx = sx; cy = sy; break;
    }
  }
  return out;
}
function bbox(cmds) {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const c of cmds) for (let i = 1; i < c.length; i += 2) { x0 = Math.min(x0, c[i]); x1 = Math.max(x1, c[i]); y0 = Math.min(y0, c[i + 1]); y1 = Math.max(y1, c[i + 1]); }
  return { x0, y0, x1, y1 };
}
const f = (n) => { const r = Math.round(n * 100) / 100; return Object.is(r, -0) ? '0' : String(r); };
function toD(cmds, m) {
  return cmds.map((c) => {
    if (c[0] === 'Z') return 'Z';
    const pts = [];
    for (let i = 1; i < c.length; i += 2) { const p = ap(m, c[i], c[i + 1]); pts.push(f(p[0]) + ' ' + f(p[1])); }
    return c[0] + pts.join(' ');
  }).join('');
}

// ---------- Màu, gradient ----------
function color(v, alpha) {
  v = v.trim();
  let r, g, b;
  if (v[0] === '#') {
    let h = v.slice(1); if (h.length === 3) h = h.split('').map((x) => x + x).join('');
    r = parseInt(h.slice(0, 2), 16); g = parseInt(h.slice(2, 4), 16); b = parseInt(h.slice(4, 6), 16);
  } else throw new Error('color ' + v);
  const a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
  return '0x' + [a, r, g, b].map((x) => x.toString(16).padStart(2, '0')).join('').toUpperCase();
}
function stops(grad, alpha) {
  return grad.children.filter((c) => c.name === 'stop').map((st) => {
    const so = st.attrs['stop-opacity'] != null ? Number(st.attrs['stop-opacity']) : 1;
    return `${f(Number(st.attrs.offset))}f to Color(${color(st.attrs['stop-color'], so * alpha)})`;
  }).join(', ');
}
// Trả về biểu thức Kotlin cho paint: Color(...) hoặc Brush
function paint(ref, cmds, m, alpha) {
  const u = /url\(#([^)]+)\)/.exec(ref);
  if (!u) return { kind: 'color', expr: `Color(${color(ref, alpha)})` };
  const g = ids[u[1]];
  if (!g) throw new Error('missing ' + u[1]);
  const bb = bbox(cmds);
  const bx = (fx, fy) => ap(m, bb.x0 + fx * (bb.x1 - bb.x0), bb.y0 + fy * (bb.y1 - bb.y0));
  if (g.name === 'linearGradient') {
    const a = g.attrs;
    const p0 = bx(Number(a.x1 ?? 0), Number(a.y1 ?? 0)), p1 = bx(Number(a.x2 ?? 1), Number(a.y2 ?? 0));
    return { kind: 'brush', expr: `Brush.linearGradient(${stops(g, alpha)}, start = Offset(${f(p0[0])}f, ${f(p0[1])}f), end = Offset(${f(p1[0])}f, ${f(p1[1])}f))` };
  }
  if (g.name === 'radialGradient') {
    const c = bx(0.5, 0.5), e = bx(1, 0.5);
    const r = Math.hypot(e[0] - c[0], e[1] - c[1]);
    return { kind: 'brush', expr: `Brush.radialGradient(${stops(g, alpha)}, center = Offset(${f(c[0])}f, ${f(c[1])}f), radius = ${f(r)}f)` };
  }
  throw new Error('paint ' + g.name);
}

// ---------- Duyệt cây ----------
const INHERIT = ['fill', 'stroke', 'stroke-width', 'stroke-linejoin', 'stroke-linecap', 'fill-opacity', 'stroke-opacity', 'font-family', 'font-weight', 'font-size', 'letter-spacing', 'text-anchor'];
const layers = { back: [], front: [] };
let cur = 'back';

function shapeCmds(n) {
  const a = n.attrs, N = (k, d = 0) => (a[k] != null ? Number(a[k]) : d);
  switch (n.name) {
    case 'polygon': return polyCmds(a.points, true);
    case 'polyline': return polyCmds(a.points, false);
    case 'line': return [['M', N('x1'), N('y1')], ['L', N('x2'), N('y2')]];
    case 'rect': return rectCmds(N('x'), N('y'), N('width'), N('height'), N('rx'), N('ry'));
    case 'circle': return ellipseCmds(N('cx'), N('cy'), N('r'), N('r'));
    case 'ellipse': return ellipseCmds(N('cx'), N('cy'), N('rx'), N('ry'));
    case 'path': return pathCmds(a.d);
  }
  return null;
}

function emitShape(n, st, m) {
  const cmds = shapeCmds(n);
  const d = toD(cmds, m);
  const scale = Math.sqrt(Math.abs(m[0] * m[3] - m[1] * m[2]));
  const out = layers[cur];
  const fill = st.fill ?? '#000';
  if (fill !== 'none' && n.name !== 'line' && n.name !== 'polyline') {
    const p = paint(fill, cmds, m, st.opacity * (st['fill-opacity'] != null ? Number(st['fill-opacity']) : 1));
    out.push(`fill("${d}", ${p.kind === 'color' ? p.expr : 'brush = ' + p.expr})`);
  }
  if (st.stroke && st.stroke !== 'none') {
    const p = paint(st.stroke, cmds, m, st.opacity * (st['stroke-opacity'] != null ? Number(st['stroke-opacity']) : 1));
    const w = Number(st['stroke-width'] ?? 1) * scale;
    const cap = st['stroke-linecap'] === 'round' ? ', cap = StrokeCap.Round' : '';
    const join = st['stroke-linejoin'] === 'round' ? ', join = StrokeJoin.Round' : '';
    out.push(`stroke("${d}", ${p.expr}, ${f(w)}f${cap}${join})`);
  }
}

function emitText(n, st, m) {
  // Văn bản rất nhỏ trên thiết bị (≈ 1–3 dp); vẽ theo ma trận cuối, cỡ chữ theo đơn vị cục bộ.
  const a = n.attrs;
  const tm = mul(m, parseTransform(a.transform));
  const runs = [];
  const base = { fill: st.fill, size: Number(st['font-size']), weight: Number(st['font-weight'] ?? 400), ls: Number(st['letter-spacing'] ?? 0) };
  for (const c of n.children) {
    if (c.name === '#text') runs.push({ ...base, text: c.text, dx: 0 });
    else if (c.name === 'tspan') runs.push({ ...base, fill: c.attrs.fill ?? base.fill, size: Number(c.attrs['font-size'] ?? base.size), text: c.children.map((x) => x.text).join(''), dx: Number(c.attrs.dx ?? 0) });
  }
  const x = Number(a.x ?? 0), y = Number(a.y ?? 0);
  const anchor = st['text-anchor'] === 'middle' ? 'TextAnchor.Middle' : 'TextAnchor.Start';
  const r = runs.map((r) => `TextRun("${r.text}", Color(${color(r.fill, st.opacity)}), ${f(r.size)}f, ${r.weight}, ${f(r.ls)}f, ${f(r.dx)}f)`).join(', ');
  layers[cur].push(`text(floatArrayOf(${tm.map((v) => f(v) + 'f').join(', ')}), ${f(x)}f, ${f(y)}f, ${anchor}, listOf(${r}))`);
}

function walk(n, st, m) {
  if (n.name === 'defs' || n.name === 'filter' || n.name === 'linearGradient' || n.name === 'radialGradient' || n.name === 'clipPath') return;
  if (n.name === 'animate' || n.name === '#text') return;
  const a = n.attrs || {};
  if (a['aria-label'] === 'Đường năng lượng') { cur = 'front'; return; }
  const s2 = { ...st };
  for (const k of INHERIT) if (a[k] != null) s2[k] = a[k];
  s2.opacity = st.opacity * (a.opacity != null ? Number(a.opacity) : 1);
  const m2 = n.name === 'text' ? m : mul(m, parseTransform(a.transform));
  const clip = /url\(#([^)]+)\)/.exec(a['clip-path'] || '');
  if (clip) {
    const cp = ids[clip[1]].children[0];
    layers[cur].push(`clipStart("${toD(shapeCmds(cp), m2)}")`);
  }
  if (n.name === 'use') {
    const ref = ids[(a.href || a['xlink:href']).slice(1)];
    for (const c of ref.children) walk(c, s2, m2);
  } else if (n.name === 'text') {
    emitText(n, s2, m2);
  } else if (shapeCmds(n)) {
    emitShape(n, s2, m2);
  }
  if (n.name !== 'use' && n.name !== 'text') for (const c of n.children) walk(c, s2, m2);
  if (clip) layers[cur].push('clipEnd()');
}

const svgNode = tree.children.find((c) => c.name === 'svg');
walk(svgNode, { opacity: 1 }, ROOT);

const body = (list) => list.map((l) => '        ' + l + ',').join('\n');
const kt = `package vn.ggpower.inverter.ui.home.house

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin

// File SINH TỰ ĐỘNG từ app/gg-inverter-design/design/screens/Main.dc.html (SVG ngôi nhà, khối vw.house)
// bằng app/tools/svg2kt.mjs — không sửa tay; sửa thiết kế rồi chạy lại công cụ.
// Toạ độ đã quy về khung 700 × 690 của prototype (viewBox "28 2 270 213" trong 700 × 552).

/** Lớp dưới: cây, ngôi nhà, mái, tấm pin, cột điện — vẽ trước các đường năng lượng. */
internal val HouseBack: List<ArtOp> = listOf(
${body(layers.back)}
)

/** Lớp trên: tấm pin đầu tiên (che đường PV), Energy Hub, biến tần — vẽ sau các đường năng lượng. */
internal val HouseFront: List<ArtOp> = listOf(
${body(layers.front)}
)
`;
fs.writeFileSync(outFile, kt);
console.log('back', layers.back.length, 'front', layers.front.length);
