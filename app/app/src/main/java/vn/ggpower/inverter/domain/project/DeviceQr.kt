package vn.ggpower.inverter.domain.project

/** Mã QR trên tem thiết bị đã đọc được (đặc tả §3.18). */
data class DeviceQr(val model: String, val serial: String)

private val QR_FORMAT = Regex("^GGP:([A-Z0-9]+):([A-Z0-9]+):([0-9A-F]{2})$")

/** Checksum của tem: XOR từng byte của phần đứng trước dấu hai chấm cuối, viết 2 ký tự hex hoa (đặc tả §3.18). */
fun qrChecksum(payload: String): String {
    var x = 0
    payload.toByteArray(Charsets.US_ASCII).forEach { x = x xor (it.toInt() and 0xFF) }
    return x.toString(16).uppercase().padStart(2, '0')
}

/**
 * Đọc mã QR theo định dạng `GGP:<model>:<serial>:<checksum>` (toàn bộ viết hoa, không dấu cách). Kiểm tra ngoại tuyến
 * trước khi gọi backend: sai tiền tố / sai định dạng / sai checksum → null ("Mã không hợp lệ").
 * Ví dụ hợp lệ: `GGP:HYB5K:SN2609A00137:19` (ví dụ `…:7F` trong đặc tả không khớp thuật toán — OPEN_QUESTIONS).
 */
fun parseDeviceQr(text: String): DeviceQr? {
    val m = QR_FORMAT.matchEntire(text.trim()) ?: return null
    val (model, serial, checksum) = m.destructured
    if (qrChecksum(text.trim().substringBeforeLast(':')) != checksum) return null
    return DeviceQr(model, serial)
}
