package vn.ggpower.inverter.data.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import vn.ggpower.inverter.data.project.FakeProjectRepository
import vn.ggpower.inverter.domain.auth.AccountInput
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Đăng nhập giả cho tới khi có hợp đồng API. Mật khẩu đúng là mật khẩu mẫu của prototype (Login.dc.html);
 * mọi tài khoản đúng định dạng đều được chấp nhận. Có độ trễ để thấy trạng thái "đang xử lý".
 */
@Singleton
class FakeAuthRepository @Inject constructor(
    private val session: SessionStore,
    private val clock: Clock,
    /** Tài khoản vừa đăng ký chưa có dự án (đặc tả §3.13: vào Dự án trạng thái trống); đăng nhập lại có dự án mẫu. */
    private val projects: FakeProjectRepository,
) : AuthRepository {

    private val codeLimiter = CodeRequestLimiter(clock)
    private val otp = FakeOtpBook(clock)

    /** Đặt để mô phỏng lỗi mạng / máy chủ ở các lời gọi luồng Quên mật khẩu. */
    var failure: CodeSendResult? = null

    override val isLoggedIn: Flow<Boolean> = session.isLoggedIn

    override suspend fun login(account: AccountInput, password: String): LoginResult {
        delay(FAKE_DELAY_MS)
        val ok = (account is AccountInput.Email || account is AccountInput.Phone) && password == SAMPLE_PASSWORD
        if (!ok) return LoginResult.InvalidCredentials
        projects.projects = FakeProjectRepository.SAMPLE
        session.setLoggedIn(true)
        return LoginResult.Success
    }

    override suspend fun logout() {
        session.setLoggedIn(false)
    }

    override suspend fun requestResetCode(account: AccountInput): CodeSendResult {
        delay(FAKE_DELAY_MS)
        failure?.let { return it }
        if (!codeLimiter.tryAcquire(account)) return CodeSendResult.TooManyRequests
        otp.issue(account)
        return CodeSendResult.Sent
    }

    override suspend fun verifyResetCode(account: AccountInput, code: String): VerifyCodeResult {
        delay(FAKE_DELAY_MS)
        when (failure) {
            CodeSendResult.NoConnection -> return VerifyCodeResult.NoConnection
            CodeSendResult.ServerError -> return VerifyCodeResult.ServerError
            else -> Unit
        }
        return otp.verify(account, code)
    }

    override suspend fun resetPassword(token: String, newPassword: String): ResetPasswordResult {
        delay(FAKE_DELAY_MS)
        return when (failure) {
            CodeSendResult.NoConnection -> ResetPasswordResult.NoConnection
            CodeSendResult.ServerError -> ResetPasswordResult.ServerError
            else -> ResetPasswordResult.Success
        }
    }

    override suspend fun register(name: String, email: AccountInput.Email, phone: AccountInput.Phone, password: String): RegisterResult {
        delay(FAKE_DELAY_MS)
        when (failure) {
            CodeSendResult.NoConnection -> return RegisterResult.NoConnection
            CodeSendResult.ServerError -> return RegisterResult.ServerError
            else -> Unit
        }
        if (email.value in TAKEN_EMAILS) return RegisterResult.EmailTaken
        if (phone.value in TAKEN_PHONES) return RegisterResult.PhoneTaken
        codeLimiter.tryAcquire(phone)
        otp.issue(phone)
        return RegisterResult.CodeSent
    }

    override suspend fun resendRegistrationCode(phone: AccountInput.Phone): CodeSendResult {
        delay(FAKE_DELAY_MS)
        failure?.let { return it }
        if (!codeLimiter.tryAcquire(phone)) return CodeSendResult.TooManyRequests
        otp.issue(phone)
        return CodeSendResult.Sent
    }

    override suspend fun verifyRegistration(phone: AccountInput.Phone, code: String): VerifyCodeResult {
        delay(FAKE_DELAY_MS)
        when (failure) {
            CodeSendResult.NoConnection -> return VerifyCodeResult.NoConnection
            CodeSendResult.ServerError -> return VerifyCodeResult.ServerError
            else -> Unit
        }
        val result = otp.verify(phone, code)
        if (result is VerifyCodeResult.Valid) {
            projects.projects = emptyList()
            session.setLoggedIn(true)
        }
        return result
    }

    companion object {
        const val SAMPLE_PASSWORD = "matkhau123"

        /** Tài khoản mẫu đã có (Me.dc.html: demo@ggp.vn) để thử lỗi trùng; số 0900000000 dùng thử lỗi trùng số điện thoại. */
        val TAKEN_EMAILS = setOf("demo@ggp.vn")
        val TAKEN_PHONES = setOf("0900000000")
        private const val FAKE_DELAY_MS = 800L
    }
}
