package vn.ggpower.inverter.ui.forgot

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.auth.AuthRepository
import vn.ggpower.inverter.data.auth.LoginResult
import vn.ggpower.inverter.data.auth.CodeSendResult
import vn.ggpower.inverter.data.auth.RegisterResult
import vn.ggpower.inverter.data.auth.ResetPasswordResult
import vn.ggpower.inverter.domain.auth.PasswordError
import vn.ggpower.inverter.data.auth.VerifyCodeResult
import vn.ggpower.inverter.domain.auth.AccountInput
import vn.ggpower.inverter.ui.login.AccountError
import vn.ggpower.inverter.ui.otp.OtpError

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordControllerTest {

    private class StubAuth(var result: CodeSendResult, var verify: VerifyCodeResult = VerifyCodeResult.Valid("t")) : AuthRepository {
        val requests = mutableListOf<AccountInput>()
        val codes = mutableListOf<String>()
        override val isLoggedIn = MutableStateFlow(false)
        override suspend fun login(account: AccountInput, password: String) = LoginResult.Success
        override suspend fun logout() = Unit
        override suspend fun requestResetCode(account: AccountInput): CodeSendResult {
            requests += account
            return result
        }
        override suspend fun verifyResetCode(account: AccountInput, code: String): VerifyCodeResult {
            codes += code
            return verify
        }
        var reset: ResetPasswordResult = ResetPasswordResult.Success
        val passwords = mutableListOf<Pair<String, String>>()
        override suspend fun resetPassword(token: String, newPassword: String): ResetPasswordResult {
            passwords += token to newPassword
            return reset
        }
        override suspend fun register(name: String, email: AccountInput.Email, phone: AccountInput.Phone, password: String): RegisterResult = RegisterResult.CodeSent
        override suspend fun resendRegistrationCode(phone: AccountInput.Phone): CodeSendResult = CodeSendResult.Sent
        override suspend fun verifyRegistration(phone: AccountInput.Phone, code: String): VerifyCodeResult = VerifyCodeResult.Expired
    }

    /** Đã gửi mã thành công tới 0912 345 678, đang ở 12b. */
    private fun TestScope.atOtp(auth: StubAuth): ForgotPasswordController {
        val c = ForgotPasswordController("0912 345 678", auth, backgroundScope)
        c.onSendCode()
        runCurrent()
        return c
    }

    @Test fun `vao 12b hien noi nhan da che va dem nguoc 60 giay`() = runTest {
        val c = atOtp(StubAuth(CodeSendResult.Sent))
        assertEquals("0912 ••• 678", c.otp.value.destination)
        assertEquals(60, c.otp.value.resendIn)
        advanceTimeBy(15_000); runCurrent()
        assertEquals(45, c.otp.value.resendIn)
        advanceTimeBy(45_000); runCurrent()
        assertEquals(0, c.otp.value.resendIn)
    }

    @Test fun `chua du 6 so thi khong goi kiem tra, ma dung thi sang 12c`() = runTest {
        val auth = StubAuth(CodeSendResult.Sent, VerifyCodeResult.Valid("tok"))
        val c = atOtp(auth)
        var verified = 0
        backgroundScope.launch { c.codeVerified.collect { verified++ } }
        c.onCodeChange("48291")
        c.onVerify()
        runCurrent()
        assertTrue(auth.codes.isEmpty())
        c.onCodeChange("482913")
        c.onVerify()
        runCurrent()
        assertEquals(listOf("482913"), auth.codes)
        assertEquals(1, verified)
        assertEquals("tok", c.resetToken)
    }

    @Test fun `ma sai thi bao so lan con lai, sua ma thi an loi`() = runTest {
        val c = atOtp(StubAuth(CodeSendResult.Sent, VerifyCodeResult.Wrong(4)))
        c.onCodeChange("000000")
        c.onVerify()
        runCurrent()
        assertEquals(OtpError.Wrong(4), c.otp.value.error)
        assertFalse(c.otp.value.verifying)
        c.onCodeChange("00000")
        assertNull(c.otp.value.error)
    }

    @Test fun `gui lai ma chi khi het dem nguoc, gui xong xoa ma va dem lai`() = runTest {
        val auth = StubAuth(CodeSendResult.Sent)
        val c = atOtp(auth)
        c.onCodeChange("123")
        c.onResend()
        runCurrent()
        assertEquals(1, auth.requests.size)
        advanceTimeBy(60_000); runCurrent()
        c.onResend()
        runCurrent()
        assertEquals(2, auth.requests.size)
        assertEquals("", c.otp.value.code)
        assertEquals(60, c.otp.value.resendIn)
        advanceTimeBy(60_000); runCurrent()
        auth.result = CodeSendResult.TooManyRequests
        c.onResend()
        runCurrent()
        assertEquals(OtpError.TooManyRequests, c.otp.value.error)
        assertEquals(0, c.otp.value.resendIn)
    }

    @Test fun `dien san tai khoan tu Dang nhap`() = runTest {
        val c = ForgotPasswordController("0912 345 678", StubAuth(CodeSendResult.Sent), backgroundScope)
        assertEquals("0912 345 678", c.find.value.account)
    }

    @Test fun `bo trong hoac sai dinh dang thi bao loi duoi o, khong goi backend`() = runTest {
        val auth = StubAuth(CodeSendResult.Sent)
        val c = ForgotPasswordController("", auth, backgroundScope)
        c.onSendCode()
        assertEquals(AccountError.Empty, c.find.value.accountError)
        c.onAccountChange("0912")
        assertNull(c.find.value.accountError)
        c.onSendCode()
        assertEquals(AccountError.Invalid, c.find.value.accountError)
        assertTrue(auth.requests.isEmpty())
    }

    @Test fun `gui duoc ma thi chuyen sang 12b va nho noi nhan`() = runTest {
        val auth = StubAuth(CodeSendResult.Sent)
        val c = ForgotPasswordController("0912 345 678", auth, backgroundScope)
        var sent = 0
        backgroundScope.launch { c.codeSent.collect { sent++ } }
        c.onSendCode()
        runCurrent()
        assertEquals(1, sent)
        assertEquals(AccountInput.Phone("0912345678"), c.destination)
        assertFalse(c.find.value.sending)
        assertNull(c.find.value.formError)
    }

    @Test fun `qua so lan gui hoac loi mang thi hien hop loi, o lai 12a`() = runTest {
        val auth = StubAuth(CodeSendResult.TooManyRequests)
        val c = ForgotPasswordController("demo@ggp.vn", auth, backgroundScope)
        var sent = 0
        backgroundScope.launch { c.codeSent.collect { sent++ } }
        c.onSendCode()
        runCurrent()
        assertEquals(FindError.TooManyRequests, c.find.value.formError)
        auth.result = CodeSendResult.NoConnection
        c.onSendCode()
        runCurrent()
        assertEquals(FindError.NoConnection, c.find.value.formError)
        assertEquals(0, sent)
        assertNull(c.destination)
        c.onAccountChange("demo@ggp.vn ")
        assertNull(c.find.value.formError)
    }

    /** Đã qua 12b với mã đúng, đang ở 12c. */
    private fun TestScope.atNew(auth: StubAuth): ForgotPasswordController {
        val c = atOtp(auth)
        c.onCodeChange("123456")
        c.onVerify()
        runCurrent()
        return c
    }

    @Test fun `mat khau yeu hoac nhap lai khong khop thi bao loi duoi o, khong goi backend`() = runTest {
        val auth = StubAuth(CodeSendResult.Sent, VerifyCodeResult.Valid("tok"))
        val c = atNew(auth)
        c.onChangePassword()
        assertEquals(PasswordError.Empty, c.newPassword.value.passwordError)
        assertEquals(PasswordError.ConfirmEmpty, c.newPassword.value.confirmError)
        c.onPasswordChange("abc12")
        c.onConfirmChange("abc")
        assertNull(c.newPassword.value.passwordError)
        c.onChangePassword()
        assertEquals(PasswordError.TooWeak, c.newPassword.value.passwordError)
        assertEquals(PasswordError.Mismatch, c.newPassword.value.confirmError)
        assertTrue(auth.passwords.isEmpty())
    }

    @Test fun `doi mat khau thanh cong thi sang 12d, dung ma xac nhan cua 12b`() = runTest {
        val auth = StubAuth(CodeSendResult.Sent, VerifyCodeResult.Valid("tok"))
        val c = atNew(auth)
        var changed = 0
        backgroundScope.launch { c.passwordChanged.collect { changed++ } }
        c.onPasswordChange("GGpower2026")
        c.onConfirmChange("GGpower2026")
        c.onChangePassword()
        runCurrent()
        assertEquals(listOf("tok" to "GGpower2026"), auth.passwords)
        assertEquals(1, changed)
    }

    @Test fun `phien het han thi hien hop loi`() = runTest {
        val auth = StubAuth(CodeSendResult.Sent, VerifyCodeResult.Valid("tok")).apply { reset = ResetPasswordResult.Expired }
        val c = atNew(auth)
        c.onPasswordChange("GGpower2026")
        c.onConfirmChange("GGpower2026")
        c.onChangePassword()
        runCurrent()
        assertEquals(NewPasswordError.Expired, c.newPassword.value.formError)
        assertFalse(c.newPassword.value.saving)
    }
}
