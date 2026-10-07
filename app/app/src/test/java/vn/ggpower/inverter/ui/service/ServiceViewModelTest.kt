package vn.ggpower.inverter.ui.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import vn.ggpower.inverter.data.support.FakeSupportContactRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `hien lien he mau va chua sao chep dong nao`() {
        val vm = ServiceViewModel(FakeSupportContactRepository())
        assertEquals("1900 6868", vm.state.value.contact.hotline)
        assertEquals("support@ggpower.vn", vm.state.value.contact.email)
        assertNull(vm.state.value.copied)
    }

    @Test fun `sao chep thi hien phan hoi va tu het sau 2 giay`() = runTest(dispatcher) {
        val vm = ServiceViewModel(FakeSupportContactRepository())
        vm.onCopied(ContactField.Hotline)
        assertEquals(ContactField.Hotline, vm.state.value.copied)
        advanceTimeBy(COPIED_FEEDBACK_MS - 1)
        runCurrent()
        assertEquals(ContactField.Hotline, vm.state.value.copied)
        advanceTimeBy(1)
        runCurrent()
        assertNull(vm.state.value.copied)
    }

    @Test fun `sao chep dong khac thi chuyen phan hoi va dem lai tu dau`() = runTest(dispatcher) {
        val vm = ServiceViewModel(FakeSupportContactRepository())
        vm.onCopied(ContactField.Hotline)
        advanceTimeBy(1_500)
        runCurrent()
        vm.onCopied(ContactField.Email)
        assertEquals(ContactField.Email, vm.state.value.copied)
        // Hẹn giờ của lần đầu đã huỷ: tới mốc 2 s tính từ lần đầu vẫn còn phản hồi.
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(ContactField.Email, vm.state.value.copied)
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(vm.state.value.copied)
    }
}
