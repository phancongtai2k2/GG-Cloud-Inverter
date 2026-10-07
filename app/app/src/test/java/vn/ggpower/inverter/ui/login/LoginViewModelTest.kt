package vn.ggpower.inverter.ui.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.ggpower.inverter.data.auth.AuthRepository
import vn.ggpower.inverter.data.auth.LoginResult
import vn.ggpower.inverter.data.auth.CodeSendResult
import vn.ggpower.inverter.data.auth.RegisterResult
import vn.ggpower.inverter.data.auth.ResetPasswordResult
import vn.ggpower.inverter.data.auth.VerifyCodeResult
import vn.ggpower.inverter.domain.auth.AccountInput

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class StubAuth(var result: LoginResult) : AuthRepository {
        val calls = mutableListOf<Pair<AccountInput, String>>()
        override val isLoggedIn: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun login(account: AccountInput, password: String): LoginResult {
            calls += account to password
            return result
        }
        override suspend fun logout() = Unit
        override suspend fun requestResetCode(account: AccountInput) = CodeSendResult.Sent
        override suspend fun verifyResetCode(account: AccountInput, code: String) = VerifyCodeResult.Expired
        override suspend fun resetPassword(token: String, newPassword: String) = ResetPasswordResult.Success
        override suspend fun register(name: String, email: AccountInput.Email, phone: AccountInput.Phone, password: String): RegisterResult = RegisterResult.CodeSent
        override suspend fun resendRegistrationCode(phone: AccountInput.Phone): CodeSendResult = CodeSendResult.Sent
        override suspend fun verifyRegistration(phone: AccountInput.Phone, code: String): VerifyCodeResult = VerifyCodeResult.Expired
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `bo trong ca hai o thi bao loi duoi o va khong goi dang nhap`() = runTest(dispatcher) {
        val auth = StubAuth(LoginResult.Success)
        val vm = LoginViewModel(auth)
        vm.onSubmit()
        assertEquals(AccountError.Empty, vm.state.value.accountError)
        assertTrue(vm.state.value.passwordEmpty)
        assertTrue(auth.calls.isEmpty())
    }

    @Test fun `sai dinh dang tai khoan`() = runTest(dispatcher) {
        val vm = LoginViewModel(StubAuth(LoginResult.Success))
        vm.onAccountChange("0912 345")
        vm.onPasswordChange("matkhau123")
        vm.onSubmit()
        assertEquals(AccountError.Invalid, vm.state.value.accountError)
        assertFalse(vm.state.value.passwordEmpty)
    }

    @Test fun `sua o thi xoa loi cua o do`() = runTest(dispatcher) {
        val vm = LoginViewModel(StubAuth(LoginResult.Success))
        vm.onSubmit()
        vm.onAccountChange("0")
        assertNull(vm.state.value.accountError)
        assertTrue(vm.state.value.passwordEmpty)
        vm.onPasswordChange("x")
        assertFalse(vm.state.value.passwordEmpty)
    }

    @Test fun `sai thong tin hien hop loi va het dang xu ly`() = runTest(dispatcher) {
        val auth = StubAuth(LoginResult.InvalidCredentials)
        val vm = LoginViewModel(auth)
        vm.onAccountChange("0912 345 678")
        vm.onPasswordChange("sai")
        vm.onSubmit()
        assertTrue(vm.state.value.submitting)
        advanceUntilIdle()
        assertFalse(vm.state.value.submitting)
        assertEquals(LoginFormError.InvalidCredentials, vm.state.value.formError)
        assertEquals(AccountInput.Phone("0912345678") to "sai", auth.calls.single())
    }

    @Test fun `mat mang`() = runTest(dispatcher) {
        val vm = LoginViewModel(StubAuth(LoginResult.NoConnection))
        vm.onAccountChange("an@ggpower.vn")
        vm.onPasswordChange("matkhau123")
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(LoginFormError.NoConnection, vm.state.value.formError)
    }

    @Test fun `dang nhap thanh cong phat su kien`() = runTest(dispatcher) {
        val vm = LoginViewModel(StubAuth(LoginResult.Success))
        vm.onAccountChange("0912 345 678")
        vm.onPasswordChange("matkhau123")
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(Unit, vm.loggedIn.first())
        assertNull(vm.state.value.formError)
    }

    @Test fun `bam lien tiep khi dang xu ly chi goi mot lan`() = runTest(dispatcher) {
        val auth = StubAuth(LoginResult.Success)
        val vm = LoginViewModel(auth)
        vm.onAccountChange("0912 345 678")
        vm.onPasswordChange("matkhau123")
        vm.onSubmit()
        vm.onSubmit()
        advanceUntilIdle()
        assertEquals(1, auth.calls.size)
    }
}
