package vn.ggpower.inverter.domain.account

/** Lỗi ô Họ và tên của màn 15 (đặc tả §3.15: không để trống). */
enum class ProfileNameError { Empty }

/** Phần hồ sơ sửa được ngay trên màn 15: họ tên và ảnh đại diện. Email, số điện thoại, mật khẩu đổi ở màn riêng (25, 26). */
data class ProfileDraft(val name: String, val avatarUri: String?) {

    /** Có thay đổi chưa lưu so với [saved] (đặc tả §3.15: nút Lưu chỉ bật khi có thay đổi). Khoảng trắng đầu / cuối tên không tính. */
    fun hasChangesFrom(saved: UserProfile): Boolean = name.trim() != saved.name || avatarUri != saved.avatarUri

    fun nameError(): ProfileNameError? = if (name.isBlank()) ProfileNameError.Empty else null

    /** Hồ sơ sau khi lưu: tên bỏ khoảng trắng đầu / cuối. */
    fun applyTo(saved: UserProfile): UserProfile = saved.copy(name = name.trim(), avatarUri = avatarUri)

    companion object {
        fun of(profile: UserProfile) = ProfileDraft(profile.name, profile.avatarUri)
    }
}
