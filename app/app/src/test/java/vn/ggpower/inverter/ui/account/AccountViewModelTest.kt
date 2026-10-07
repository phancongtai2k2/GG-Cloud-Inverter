package vn.ggpower.inverter.ui.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.ggpower.inverter.data.account.DeleteAccountResult
import vn.ggpower.inverter.data.account.ProfileRepository
import vn.ggpower.inverter.data.account.SaveProfileResult
import vn.ggpower.inverter.domain.account.NotificationPrefs
import vn.ggpower.inverter.domain.account.ProfileNameError
import vn.ggpower.inverter.domain.account.UserProfile
import vn.ggpower.inverter.domain.settings.AppLanguage

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class StubProfiles(
        val saveResult: SaveProfileResult = SaveProfileResult.Success,
        val deleteResult: DeleteAccountResult = DeleteAccountResult.Requested,
    ) : ProfileRepository {
        val profile = MutableStateFlow(UserProfile("Michael Demo", "demo@ggp.vn", "0912 345 678"))
        val saves = mutableListOf<Pair<String, String?>>()
        val deletes = mutableListOf<String>()
        override suspend fun getProfile() = profile.value
        override fun observeProfile(): Flow<UserProfile> = profile
        override suspend fun setNotifications(prefs: NotificationPrefs) = Unit
        override suspend fun setLanguage(language: AppLanguage) = Unit
        override suspend fun updateProfile(name: String, avatarUri: String?): SaveProfileResult {
            saves += name to avatarUri
            if (saveResult == SaveProfileResult.Success) profile.value = profile.value.copy(name = name, avatarUri = avatarUri)
            return saveResult
        }
        override suspend fun requestDeletion(password: String): DeleteAccountResult {
            deletes += password
            return deleteResult
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val AccountViewModel.content get() = state.value as AccountUiState.Content

    private fun TestScope.eventsOf(vm: AccountViewModel): List<AccountEvent> {
        val list = mutableListOf<AccountEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { list += it } }
        return list
    }

    @Test fun `mo man thi nut Luu khoa, sua ten thi mo`() = runTest(dispatcher) {
        val vm = AccountViewModel(StubProfiles())
        advanceUntilIdle()
        assertFalse(vm.content.hasChanges)
        vm.onNameChange("Nguyễn Văn An")
        assertTrue(vm.content.hasChanges)
    }

    @Test fun `luu ten trong thi bao loi va khong goi backend, sua o thi an loi`() = runTest(dispatcher) {
        val repo = StubProfiles()
        val vm = AccountViewModel(repo)
        advanceUntilIdle()
        vm.onNameChange("  ")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(ProfileNameError.Empty, vm.content.nameError)
        assertTrue(repo.saves.isEmpty())
        vm.onNameChange("A")
        assertNull(vm.content.nameError)
    }

    @Test fun `luu thanh cong thi bo khoang trang, het thay doi va bao da luu`() = runTest(dispatcher) {
        val repo = StubProfiles()
        val vm = AccountViewModel(repo)
        advanceUntilIdle()
        vm.onNameChange(" Nguyễn Văn An ")
        vm.onAvatarPicked("content://media/1")
        vm.onSave()
        assertTrue(vm.content.saving)
        advanceUntilIdle()
        assertEquals(listOf("Nguyễn Văn An" to "content://media/1"), repo.saves)
        assertEquals("Nguyễn Văn An", vm.content.draft.name)
        assertFalse(vm.content.hasChanges)
        assertFalse(vm.content.saving)
        assertTrue(vm.content.snackbar is AccountSnackbar.Saved)
    }

    @Test fun `luu loi mang thi giu thay doi va bao loi`() = runTest(dispatcher) {
        val vm = AccountViewModel(StubProfiles(saveResult = SaveProfileResult.NoConnection))
        advanceUntilIdle()
        vm.onNameChange("Nguyễn Văn An")
        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.content.hasChanges)
        assertTrue((vm.content.snackbar as AccountSnackbar.SaveFailed).noConnection)
    }

    @Test fun `quay lai khi khong co thay doi thi roi man ngay`() = runTest(dispatcher) {
        val vm = AccountViewModel(StubProfiles())
        val events = eventsOf(vm)
        advanceUntilIdle()
        vm.onBack()
        advanceUntilIdle()
        assertEquals(listOf(AccountEvent.Leave), events)
    }

    @Test fun `quay lai khi con thay doi thi hoi, o lai thi khong roi man, bo thay doi thi roi`() = runTest(dispatcher) {
        val vm = AccountViewModel(StubProfiles())
        val events = eventsOf(vm)
        advanceUntilIdle()
        vm.onNameChange("Nguyễn Văn An")
        vm.onBack()
        advanceUntilIdle()
        assertTrue(vm.content.discardOpen)
        assertTrue(events.isEmpty())
        vm.onDiscardCancel()
        assertFalse(vm.content.discardOpen)
        vm.onBack()
        vm.onDiscardConfirm()
        advanceUntilIdle()
        assertEquals(listOf(AccountEvent.Leave), events)
    }

    @Test fun `xoa tai khoan chua nhap mat khau thi khong gui`() = runTest(dispatcher) {
        val repo = StubProfiles()
        val vm = AccountViewModel(repo)
        advanceUntilIdle()
        vm.onDeleteOpen()
        assertFalse(vm.content.delete!!.canSubmit)
        vm.onDeleteConfirm()
        advanceUntilIdle()
        assertTrue(repo.deletes.isEmpty())
    }

    @Test fun `xoa tai khoan sai mat khau thi bao loi duoi o, go lai thi an loi`() = runTest(dispatcher) {
        val vm = AccountViewModel(StubProfiles(deleteResult = DeleteAccountResult.WrongPassword))
        val events = eventsOf(vm)
        advanceUntilIdle()
        vm.onDeleteOpen()
        vm.onDeletePasswordChange("sai12345")
        vm.onDeleteConfirm()
        advanceUntilIdle()
        assertEquals(DeleteError.WrongPassword, vm.content.delete!!.error)
        assertTrue(events.isEmpty())
        vm.onDeletePasswordChange("sai123456")
        assertNull(vm.content.delete!!.error)
    }

    @Test fun `xoa tai khoan dung mat khau thi dong hop va bao da xoa`() = runTest(dispatcher) {
        val repo = StubProfiles()
        val vm = AccountViewModel(repo)
        val events = eventsOf(vm)
        advanceUntilIdle()
        vm.onDeleteOpen()
        vm.onDeletePasswordChange("matkhau123")
        vm.onDeleteConfirm()
        assertTrue(vm.content.delete!!.submitting)
        vm.onDeleteDismiss() // đang gửi thì không đóng được
        assertNotNull(vm.content.delete)
        advanceUntilIdle()
        assertEquals(listOf("matkhau123"), repo.deletes)
        assertNull(vm.content.delete)
        assertEquals(listOf(AccountEvent.Deleted), events)
    }
}
