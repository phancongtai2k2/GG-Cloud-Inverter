package vn.ggpower.inverter.ui.support

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.ggpower.inverter.ui.service.COPIED_FEEDBACK_MS

@OptIn(ExperimentalCoroutinesApi::class)
class SupportSentViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SupportSentViewModel(SupportSentUiState("#HT-0310-0151", technical = true))

    @Test fun `hien ma phieu va chua sao chep`() {
        val state = vm().state.value
        assertEquals("#HT-0310-0151", state.code)
        assertTrue(state.technical)
        assertFalse(state.copied)
    }

    @Test fun `sao chep thi hien phan hoi va tu het sau 2 giay`() = runTest(dispatcher) {
        val vm = vm()
        vm.onCopied()
        assertTrue(vm.state.value.copied)
        advanceTimeBy(COPIED_FEEDBACK_MS - 1)
        runCurrent()
        assertTrue(vm.state.value.copied)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(vm.state.value.copied)
    }

    @Test fun `sao chep lai thi dem lai tu dau`() = runTest(dispatcher) {
        val vm = vm()
        vm.onCopied()
        advanceTimeBy(1_500)
        runCurrent()
        vm.onCopied()
        // Hẹn giờ của lần đầu đã huỷ: tới mốc 2 s tính từ lần đầu vẫn còn phản hồi.
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(vm.state.value.copied)
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(vm.state.value.copied)
    }
}
