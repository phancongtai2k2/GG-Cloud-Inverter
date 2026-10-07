package vn.ggpower.inverter.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.auth.AuthRepository
import javax.inject.Inject

/**
 * Chọn màn bắt đầu theo phiên đã lưu (đặc tả §3.1: đã đăng nhập thì lần mở sau đi thẳng vào Dự án)
 * và xử lý đăng xuất. Chỉ đọc phiên một lần lúc mở app để không đổi startDestination khi đang dùng.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    /** null khi đang đọc phiên. */
    val startLoggedIn: StateFlow<Boolean?> = flow { emit(auth.isLoggedIn.first()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.logout()
            onDone()
        }
    }
}
