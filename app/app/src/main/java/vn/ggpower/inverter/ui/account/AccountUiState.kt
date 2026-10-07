package vn.ggpower.inverter.ui.account

import vn.ggpower.inverter.domain.account.ProfileDraft
import vn.ggpower.inverter.domain.account.ProfileNameError
import vn.ggpower.inverter.domain.account.UserProfile

/** Màn 15 · Thông tin tài khoản. */
sealed interface AccountUiState {
    data object Loading : AccountUiState

    data class Content(
        /** Hồ sơ đã lưu trên backend. */
        val saved: UserProfile,
        /** Họ tên và ảnh đang sửa. */
        val draft: ProfileDraft = ProfileDraft.of(saved),
        /** Lỗi ô Họ và tên, chỉ hiện sau khi bấm Lưu; sửa ô thì ẩn. */
        val nameError: ProfileNameError? = null,
        val saving: Boolean = false,
        val snackbar: AccountSnackbar? = null,
        /** Hộp "Bỏ thay đổi?" khi rời màn còn thay đổi chưa lưu (đặc tả §3.15, BO_SUNG §4). */
        val discardOpen: Boolean = false,
        /** Hộp xoá tài khoản (Account__delete-dialog.png); null khi đóng. */
        val delete: DeleteDialogState? = null,
    ) : AccountUiState {
        val hasChanges: Boolean get() = draft.hasChangesFrom(saved)
    }
}

/** Thông báo ngắn ở đáy màn; [id] để tự ẩn đúng thông báo đang hiện. */
sealed interface AccountSnackbar {
    val id: Long

    data class Saved(override val id: Long) : AccountSnackbar
    data class SaveFailed(override val id: Long, val noConnection: Boolean) : AccountSnackbar
}

enum class DeleteError { WrongPassword, NoConnection, Server }

data class DeleteDialogState(
    val password: String = "",
    val submitting: Boolean = false,
    val error: DeleteError? = null,
) {
    /** Nút "Xoá tài khoản" khoá khi chưa nhập mật khẩu (BO_SUNG §24). */
    val canSubmit: Boolean get() = password.isNotEmpty() && !submitting
}
