package vn.ggpower.inverter.ui.qrscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.project.AddProjectRepository
import vn.ggpower.inverter.data.project.SerialCheckResult
import vn.ggpower.inverter.domain.project.DeviceIdentity
import vn.ggpower.inverter.domain.project.DeviceQr
import vn.ggpower.inverter.domain.project.parseDeviceQr
import javax.inject.Inject

/** Lỗi khi kiểm tra thiết bị của mã vừa quét (BO_SUNG §24). */
enum class ScanProblem { NotFound, OwnedByOther, NoConnection, Server }

/** Tấm kết quả dưới đáy màn 18 sau khi đọc được mã đúng định dạng. */
sealed interface ScanSheet {
    val qr: DeviceQr
    data class Checking(override val qr: DeviceQr) : ScanSheet
    data class Found(override val qr: DeviceQr, val device: DeviceIdentity) : ScanSheet
    data class Problem(override val qr: DeviceQr, val problem: ScanProblem) : ScanSheet
}

data class QrScanUiState(
    /** Hiện nhãn đỏ "Mã không hợp lệ" (2 giây, đặc tả §3.18). */
    val invalid: Boolean = false,
    val sheet: ScanSheet? = null,
    val torchOn: Boolean = false,
)

@HiltViewModel
class QrScanViewModel @Inject constructor(repository: AddProjectRepository) : ViewModel() {
    private val controller = QrScanController(repository, viewModelScope)
    val state: StateFlow<QrScanUiState> get() = controller.state
    val scanned: Flow<Unit> get() = controller.scanned
    fun onCodeDetected(raw: String) = controller.onCodeDetected(raw)
    fun onRescan() = controller.onRescan()
    fun onToggleTorch() = controller.onToggleTorch()
}

/**
 * Logic màn 18 (đặc tả §3.18): mã sai định dạng → nhãn "Mã không hợp lệ" 2 giây rồi quét tiếp, không thoát màn; mã đúng
 * → rung nhẹ, dừng nhận mã mới, hỏi backend để hiện tên thiết bị. "Quét lại" bỏ tấm kết quả và quét tiếp.
 */
class QrScanController(
    private val repository: AddProjectRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(QrScanUiState())
    val state: StateFlow<QrScanUiState> = _state.asStateFlow()

    private val _scanned = Channel<Unit>(Channel.BUFFERED)
    /** Phát một lần khi đọc được mã đúng định dạng: màn hình rung nhẹ. */
    val scanned: Flow<Unit> = _scanned.receiveAsFlow()

    private var invalidJob: Job? = null
    private var lastInvalid: String? = null

    /** Camera đọc được một mã (gọi liên tục khi mã còn trong khung). */
    fun onCodeDetected(raw: String) {
        if (_state.value.sheet != null) return
        val qr = parseDeviceQr(raw)
        if (qr == null) {
            // Cùng một mã sai còn nằm trong khung: không gia hạn nhãn mãi.
            if (raw == lastInvalid && _state.value.invalid) return
            lastInvalid = raw
            invalidJob?.cancel()
            _state.update { it.copy(invalid = true) }
            invalidJob = scope.launch {
                delay(INVALID_SHOWN_MS)
                _state.update { it.copy(invalid = false) }
            }
            return
        }
        invalidJob?.cancel()
        lastInvalid = null
        _state.update { it.copy(invalid = false, sheet = ScanSheet.Checking(qr)) }
        _scanned.trySend(Unit)
        scope.launch {
            val sheet = when (val r = repository.checkSerial(qr.serial)) {
                is SerialCheckResult.Valid -> ScanSheet.Found(qr, r.device)
                SerialCheckResult.NotFound -> ScanSheet.Problem(qr, ScanProblem.NotFound)
                SerialCheckResult.OwnedByOther -> ScanSheet.Problem(qr, ScanProblem.OwnedByOther)
                SerialCheckResult.NoConnection -> ScanSheet.Problem(qr, ScanProblem.NoConnection)
                SerialCheckResult.ServerError -> ScanSheet.Problem(qr, ScanProblem.Server)
            }
            _state.update { if (it.sheet == ScanSheet.Checking(qr)) it.copy(sheet = sheet) else it }
        }
    }

    fun onRescan() = _state.update { it.copy(sheet = null) }

    fun onToggleTorch() = _state.update { it.copy(torchOn = !it.torchOn) }

    companion object {
        const val INVALID_SHOWN_MS = 2_000L
    }
}
