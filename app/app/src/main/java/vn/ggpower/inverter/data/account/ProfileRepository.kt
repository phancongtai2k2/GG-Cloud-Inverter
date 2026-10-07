package vn.ggpower.inverter.data.account

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import vn.ggpower.inverter.data.auth.FakeAuthRepository
import vn.ggpower.inverter.domain.account.NotificationPrefs
import vn.ggpower.inverter.domain.account.UserProfile
import vn.ggpower.inverter.domain.settings.AppLanguage
import javax.inject.Inject
import javax.inject.Singleton

/** Kết quả lưu họ tên / ảnh đại diện (màn 15). */
sealed interface SaveProfileResult {
    data object Success : SaveProfileResult
    data object NoConnection : SaveProfileResult
    data object ServerError : SaveProfileResult
}

/**
 * Kết quả yêu cầu xoá tài khoản (đặc tả §3.15): không xoá ngay mà tạo phiếu, khoá đăng nhập, xoá dữ liệu sau 30 ngày.
 */
sealed interface DeleteAccountResult {
    data object Requested : DeleteAccountResult
    data object WrongPassword : DeleteAccountResult
    data object NoConnection : DeleteAccountResult
    data object ServerError : DeleteAccountResult
}

/** Hồ sơ tài khoản đang đăng nhập. Chưa có hợp đồng API (OPEN_QUESTIONS, đợt 3): dùng [FakeProfileRepository]. */
interface ProfileRepository {
    suspend fun getProfile(): UserProfile

    /** Hồ sơ hiện tại rồi mỗi lần hồ sơ đổi (màn 3 cập nhật tên / avatar sau khi lưu ở màn 15). */
    fun observeProfile(): Flow<UserProfile>

    suspend fun setNotifications(prefs: NotificationPrefs)

    /** Ngôn ngữ lưu vào hồ sơ tài khoản để đồng bộ giữa các thiết bị (đặc tả §3.19, giữ theo MAU_THUAN M7). */
    suspend fun setLanguage(language: AppLanguage)

    /** Lưu họ tên và ảnh đại diện (màn 15). [avatarUri] null: bỏ ảnh, avatar hiện chữ viết tắt. */
    suspend fun updateProfile(name: String, avatarUri: String?): SaveProfileResult

    /** Gửi yêu cầu xoá tài khoản, xác nhận bằng mật khẩu hiện tại (màn 15). */
    suspend fun requestDeletion(password: String): DeleteAccountResult
}

/** Dữ liệu mẫu của Me.dc.html / Account.dc.html; giữ thay đổi trong bộ nhớ suốt phiên chạy app. */
@Singleton
class FakeProfileRepository @Inject constructor() : ProfileRepository {
    private val profile = MutableStateFlow(SAMPLE)
    var language: AppLanguage? = null
        private set

    override suspend fun getProfile(): UserProfile {
        delay(300)
        return profile.value
    }

    override fun observeProfile(): Flow<UserProfile> = flow {
        delay(300)
        emitAll(profile)
    }

    override suspend fun setNotifications(prefs: NotificationPrefs) {
        profile.value = profile.value.copy(notifications = prefs)
    }

    override suspend fun setLanguage(language: AppLanguage) {
        this.language = language
    }

    override suspend fun updateProfile(name: String, avatarUri: String?): SaveProfileResult {
        delay(600)
        profile.value = profile.value.copy(name = name, avatarUri = avatarUri)
        return SaveProfileResult.Success
    }

    /** Mật khẩu mẫu giống Đăng nhập ([FakeAuthRepository.SAMPLE_PASSWORD]). */
    override suspend fun requestDeletion(password: String): DeleteAccountResult {
        delay(800)
        return if (password == FakeAuthRepository.SAMPLE_PASSWORD) DeleteAccountResult.Requested else DeleteAccountResult.WrongPassword
    }

    companion object {
        val SAMPLE = UserProfile(name = "Michael Demo", email = "demo@ggp.vn", phone = "0912 345 678")
    }
}
