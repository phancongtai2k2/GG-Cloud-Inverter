package vn.ggpower.inverter.ui.register

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ggpower.inverter.data.auth.AuthRepository
import vn.ggpower.inverter.data.auth.CodeSendResult
import vn.ggpower.inverter.data.auth.LoginResult
import vn.ggpower.inverter.data.auth.RegisterResult
import vn.ggpower.inverter.data.auth.ResetPasswordResult
import vn.ggpower.inverter.data.auth.VerifyCodeResult
import vn.ggpower.inverter.domain.auth.AccountInput
import vn.ggpower.inverter.domain.auth.PasswordError
import vn.ggpower.inverter.domain.auth.RegisterFieldError
import vn.ggpower.inverter.ui.otp.OtpError

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterControllerTest {

    private class StubAuth(var result: RegisterResult = RegisterResult.CodeSent, var verify: VerifyCodeResult = VerifyCodeResult.Valid("t")) : AuthRepository {
        val verified = mutableListOf<Pair<AccountInput.Phone, String>>()
        var resends = 0
        val registered = mutableListOf<List<Any>>()
        override val isLoggedIn = MutableStateFlow(false)
        override suspend fun login(account: AccountInput, password: String) = LoginResult.Success
        override suspend fun logout() = Unit
        override suspend fun requestResetCode(account: AccountInput) = CodeSendResult.Sent
        override suspend fun verifyResetCode(account: AccountInput, code: String) = VerifyCodeResult.Expired
        override suspend fun resetPassword(token: String, newPassword: String) = ResetPasswordResult.Success
        override suspend fun register(name: String, email: AccountInput.Email, phone: AccountInput.Phone, password: String): RegisterResult {
            registered += listOf(name, email, phone, password)
            return result
        }
        override suspend fun resendRegistrationCode(phone: AccountInput.Phone): CodeSendResult { resends++; return CodeSendResult.Sent }
        override suspend fun verifyRegistration(phone: AccountInput.Phone, code: String): VerifyCodeResult {
            verified += phone to code
            return verify
        }
    }

    private fun RegisterController.fill() {
        onNameChange(" Nguyễn Văn An ")
        onEmailChange("an.nguyen@gmail.com")
        onPhoneChange("0912 345 678")
        onPasswordChange("GGpower2026")
        onConfirmChange("GGpower2026")
    }

    @Test fun `chua dong y dieu khoan thi nut khoa, khong lam gi`() = runTest {
        val auth = StubAuth()
        val c = RegisterController(auth, backgroundScope)
        c.fill()
        c.onSubmit()
        runCurrent()
        assertTrue(auth.registered.isEmpty())
        assertNull(c.info.value.nameError)
    }

    @Test fun `dong y roi bam tao thi kiem tra tung o`() = runTest {
        val auth = StubAuth()
        val c = RegisterController(auth, backgroundScope)
        c.onAgreeToggle()
        c.onPhoneChange("0912")
        c.onSubmit()
        val s = c.info.value
        assertEquals(RegisterFieldError.NameEmpty, s.nameError)
        assertEquals(RegisterFieldError.EmailEmpty, s.emailError)
        assertEquals(RegisterFieldError.PhoneInvalid, s.phoneError)
        assertEquals(PasswordError.Empty, s.passwordError)
        assertTrue(auth.registered.isEmpty())
        c.onPhoneChange("0912345678")
        assertNull(c.info.value.phoneError)
    }

    @Test fun `hop le thi gui du lieu da chuan hoa va sang 13b`() = runTest {
        val auth = StubAuth()
        val c = RegisterController(auth, backgroundScope)
        var sent = 0
        backgroundScope.launch { c.codeSent.collect { sent++ } }
        c.fill()
        c.onAgreeToggle()
        c.onSubmit()
        runCurrent()
        assertEquals(
            listOf(listOf("Nguyễn Văn An", AccountInput.Email("an.nguyen@gmail.com"), AccountInput.Phone("0912345678"), "GGpower2026")),
            auth.registered,
        )
        assertEquals(1, sent)
        assertEquals(AccountInput.Phone("0912345678"), c.phone)
    }

    @Test fun `email hoac so da dang ky thi bao loi duoi o`() = runTest {
        val auth = StubAuth(RegisterResult.EmailTaken)
        val c = RegisterController(auth, backgroundScope)
        c.fill()
        c.onAgreeToggle()
        c.onSubmit()
        runCurrent()
        assertEquals(RegisterFieldError.EmailTaken, c.info.value.emailError)
        auth.result = RegisterResult.PhoneTaken
        c.onSubmit()
        runCurrent()
        assertEquals(RegisterFieldError.PhoneTaken, c.info.value.phoneError)
        assertNull(c.phone)
    }

    @Test fun `13b hien so da che, ma dung thi bao da dang ky`() = runTest {
        val auth = StubAuth()
        val c = RegisterController(auth, backgroundScope)
        var registered = 0
        backgroundScope.launch { c.registered.collect { registered++ } }
        c.fill()
        c.onAgreeToggle()
        c.onSubmit()
        runCurrent()
        assertEquals("0912 ••• 678", c.otp.value.destination)
        assertEquals(60, c.otp.value.resendIn)
        c.onCodeChange("730516")
        c.onVerify()
        runCurrent()
        assertEquals(listOf(AccountInput.Phone("0912345678") to "730516"), auth.verified)
        assertEquals(1, registered)
    }

    @Test fun `13b ma sai thi bao loi, het dem nguoc moi gui lai duoc`() = runTest {
        val auth = StubAuth(verify = VerifyCodeResult.Wrong(4))
        val c = RegisterController(auth, backgroundScope)
        c.fill()
        c.onAgreeToggle()
        c.onSubmit()
        runCurrent()
        c.onCodeChange("000000")
        c.onVerify()
        runCurrent()
        assertEquals(OtpError.Wrong(4), c.otp.value.error)
        c.onResend()
        runCurrent()
        assertEquals(0, auth.resends)
        advanceTimeBy(60_000); runCurrent()
        c.onResend()
        runCurrent()
        assertEquals(1, auth.resends)
    }
}
