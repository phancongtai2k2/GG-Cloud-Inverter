package vn.ggpower.inverter.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.auth.AuthRepository
import vn.ggpower.inverter.data.auth.LoginResult
import vn.ggpower.inverter.domain.auth.AccountInput
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _loggedIn = Channel<Unit>(Channel.BUFFERED)
    /** Phát một lần khi đăng nhập thành công; màn hình chuyển sang Dự án. */
    val loggedIn = _loggedIn.receiveAsFlow()

    fun onAccountChange(value: String) = _state.update {
        it.copy(account = value, accountError = null, formError = null)
    }

    fun onPasswordChange(value: String) = _state.update {
        it.copy(password = value, passwordEmpty = false, formError = null)
    }

    fun onTogglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun onSubmit() {
        val current = _state.value
        if (current.submitting) return
        val account = AccountInput.parse(current.account)
        val accountError = when (account) {
            AccountInput.Empty -> AccountError.Empty
            AccountInput.Invalid -> AccountError.Invalid
            else -> null
        }
        val passwordEmpty = current.password.isEmpty()
        if (accountError != null || passwordEmpty) {
            _state.update { it.copy(accountError = accountError, passwordEmpty = passwordEmpty, formError = null) }
            return
        }
        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val result = auth.login(account, current.password)
            val formError = when (result) {
                LoginResult.Success -> null
                LoginResult.InvalidCredentials -> LoginFormError.InvalidCredentials
                LoginResult.NoConnection -> LoginFormError.NoConnection
                LoginResult.ServerError -> LoginFormError.Server
            }
            _state.update { it.copy(submitting = false, formError = formError) }
            if (result == LoginResult.Success) _loggedIn.send(Unit)
        }
    }
}
