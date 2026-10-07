package vn.ggpower.inverter.ui.addproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.ggpower.inverter.data.location.LocationProvider
import vn.ggpower.inverter.data.location.LocationResult
import vn.ggpower.inverter.data.project.AddProjectRepository
import vn.ggpower.inverter.data.project.CreateProjectResult
import vn.ggpower.inverter.data.project.NewProject
import vn.ggpower.inverter.data.project.SerialCheckResult
import vn.ggpower.inverter.domain.project.Coordinates
import vn.ggpower.inverter.domain.project.DeviceIdentity
import vn.ggpower.inverter.domain.project.ProjectInfoError
import vn.ggpower.inverter.domain.project.ProjectInfoInput
import vn.ggpower.inverter.domain.project.TariffError
import vn.ggpower.inverter.domain.project.TariffInput
import vn.ggpower.inverter.domain.project.checkTariff
import vn.ggpower.inverter.domain.project.normalizeMoneyInput
import vn.ggpower.inverter.domain.project.checkProjectInfo
import vn.ggpower.inverter.domain.project.normalizeKwpInput
import vn.ggpower.inverter.domain.project.normalizeSerial
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Tên dự án mặc định (đặc tả §3.14 bước 2). */
const val DEFAULT_PROJECT_NAME = "Nhà của bạn"

/** Lỗi kiểm tra serial ở 14a (BO_SUNG §21). */
enum class SerialError { Empty, NotFound, OwnedByOther, NoConnection, Server }

/** Bước 14a · Thiết bị. "Tiếp tục" chỉ bật khi đã có [device] hợp lệ; sửa serial thì bỏ kết quả cũ. */
data class AddDeviceUiState(
    val serial: String = "",
    val error: SerialError? = null,
    val checking: Boolean = false,
    val device: DeviceIdentity? = null,
) {
    val canContinue: Boolean get() = device != null && !checking
}

/** Ghi chú dưới ô Địa chỉ sau khi bấm nút định vị (BO_SUNG §22). */
sealed interface LocateNote {
    data class Found(val coordinates: Coordinates) : LocateNote
    data object Disabled : LocateNote
    data object Denied : LocateNote
    data object Failed : LocateNote
}

/** Bước 14b · Thông tin dự án. Lỗi dưới ô hiện sau khi bấm Tiếp tục; sửa ô thì lỗi ô đó ẩn. */
data class AddInfoUiState(
    val input: ProjectInfoInput,
    val nameError: ProjectInfoError? = null,
    val addressError: ProjectInfoError? = null,
    val dateError: ProjectInfoError? = null,
    val kwpError: ProjectInfoError? = null,
    val locating: Boolean = false,
    val locateNote: LocateNote? = null,
)

/** Lỗi trong hộp đỏ của 14c (BO_SUNG §23). */
enum class CreateError { LimitReached, NoConnection, Server }

/** Bước 14c · Giá điện. [creating] / [skipping]: nút "Tạo dự án" / "Bỏ qua" đang chờ backend. */
data class AddTariffUiState(
    val input: TariffInput = TariffInput(),
    val fixedError: TariffError? = null,
    val creating: Boolean = false,
    val skipping: Boolean = false,
    val formError: CreateError? = null,
) {
    val busy: Boolean get() = creating || skipping
}

/** ViewModel của cả luồng Thêm dự án (14a → 14c), gắn với nested graph `AddProjectFlow`. */
@HiltViewModel
class AddProjectViewModel @Inject constructor(
    repository: AddProjectRepository,
    location: LocationProvider,
    clock: Clock,
) : ViewModel() {
    private val controller = AddProjectController(repository, location, clock, viewModelScope)
    val device: StateFlow<AddDeviceUiState> get() = controller.device
    fun onSerialChange(value: String) = controller.onSerialChange(value)
    fun onCheck() = controller.onCheck()
    fun onScanned(serial: String) = controller.onScanned(serial)
    val info: StateFlow<AddInfoUiState> get() = controller.info
    val infoDone: Flow<Unit> get() = controller.infoDone
    fun onInfoChange(transform: (ProjectInfoInput) -> ProjectInfoInput) = controller.onInfoChange(transform)
    fun onLocate() = controller.onLocate()
    fun onLocationDenied() = controller.onLocationDenied()
    fun onInfoContinue() = controller.onInfoContinue()
    val tariff: StateFlow<AddTariffUiState> get() = controller.tariff
    val created: Flow<String> get() = controller.created
    fun onTariffChange(transform: (TariffInput) -> TariffInput) = controller.onTariffChange(transform)
    fun onCreate() = controller.onCreate(skipTariff = false)
    fun onSkip() = controller.onCreate(skipTariff = true)
}

/** Tách khỏi ViewModel để test. */
class AddProjectController(
    private val repository: AddProjectRepository,
    private val location: LocationProvider,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val today: LocalDate get() = LocalDate.now(clock)

    /** Tên mặc định "Nhà của bạn" (đặc tả §3.14), ngày lắp đặt mặc định hôm nay. */
    private val _info = MutableStateFlow(AddInfoUiState(ProjectInfoInput(name = DEFAULT_PROJECT_NAME, installedOn = today)))
    val info: StateFlow<AddInfoUiState> = _info.asStateFlow()

    private val _infoDone = Channel<Unit>(Channel.BUFFERED)
    /** Phát một lần khi 14b hợp lệ: chuyển sang 14c. */
    val infoDone: Flow<Unit> = _infoDone.receiveAsFlow()

    /** Sửa một ô của 14b; ẩn lỗi của ô vừa đổi. */
    fun onInfoChange(transform: (ProjectInfoInput) -> ProjectInfoInput) = _info.update {
        val before = it.input
        val after = transform(before).let { i -> i.copy(pvKwp = normalizeKwpInput(i.pvKwp)) }
        it.copy(
            input = after,
            nameError = if (after.name != before.name) null else it.nameError,
            addressError = if (after.address != before.address) null else it.addressError,
            dateError = if (after.installedOn != before.installedOn) null else it.dateError,
            kwpError = if (after.pvKwp != before.pvKwp) null else it.kwpError,
        )
    }

    /** Bấm nút định vị (đã có quyền): lấy toạ độ; ô địa chỉ đang trống thì điền địa chỉ tra được. */
    fun onLocate() {
        if (_info.value.locating) return
        _info.update { it.copy(locating = true, locateNote = null) }
        scope.launch {
            val result = location.currentLocation()
            _info.update {
                when (result) {
                    is LocationResult.Found -> it.copy(
                        locating = false,
                        locateNote = LocateNote.Found(result.coordinates),
                        input = it.input.copy(
                            coordinates = result.coordinates,
                            address = if (it.input.address.isBlank()) result.address ?: it.input.address else it.input.address,
                        ),
                        addressError = if (it.input.address.isBlank() && result.address != null) null else it.addressError,
                    )
                    LocationResult.Disabled -> it.copy(locating = false, locateNote = LocateNote.Disabled)
                    LocationResult.Failed -> it.copy(locating = false, locateNote = LocateNote.Failed)
                }
            }
        }
    }

    fun onLocationDenied() = _info.update { it.copy(locating = false, locateNote = LocateNote.Denied) }

    fun onInfoContinue() {
        val check = checkProjectInfo(_info.value.input, today)
        _info.update {
            it.copy(nameError = check.nameError, addressError = check.addressError, dateError = check.dateError, kwpError = check.kwpError)
        }
        if (check.valid) _infoDone.trySend(Unit)
    }

    private val _tariff = MutableStateFlow(AddTariffUiState())
    val tariff: StateFlow<AddTariffUiState> = _tariff.asStateFlow()

    private val _created = Channel<String>(Channel.BUFFERED)
    /** Phát id dự án vừa tạo: chuyển sang Màn hình chính của dự án đó. */
    val created: Flow<String> = _created.receiveAsFlow()

    /** Sửa ô của 14c; ô tiền tự chèn dấu chấm ngăn nghìn. */
    fun onTariffChange(transform: (TariffInput) -> TariffInput) = _tariff.update {
        val next = transform(it.input).let { i -> i.copy(fixedPrice = normalizeMoneyInput(i.fixedPrice), sellPrice = normalizeMoneyInput(i.sellPrice)) }
        it.copy(input = next, fixedError = if (next.fixedPrice != it.input.fixedPrice || next.kind != it.input.kind) null else it.fixedError, formError = null)
    }

    /**
     * "Tạo dự án" (kèm giá điện) hoặc "Bỏ qua" (không kèm, cài sau): cả hai đều tạo dự án (đặc tả §3.14). Cần thiết bị
     * hợp lệ ở 14a và thông tin hợp lệ ở 14b.
     */
    fun onCreate(skipTariff: Boolean) {
        val current = _tariff.value
        if (current.busy) return
        val device = _device.value.device ?: return
        val info = checkProjectInfo(_info.value.input, today)
        val kwp = info.kwp ?: return
        val tariff = if (skipTariff) null else {
            val check = checkTariff(current.input)
            if (check.fixedError != null) {
                _tariff.update { it.copy(fixedError = check.fixedError) }
                return
            }
            check.tariff
        }
        _tariff.update { it.copy(creating = !skipTariff, skipping = skipTariff, formError = null) }
        scope.launch {
            val result = repository.createProject(NewProject(device, _info.value.input, kwp, tariff))
            _tariff.update {
                it.copy(
                    creating = false,
                    skipping = false,
                    formError = when (result) {
                        is CreateProjectResult.Created -> null
                        CreateProjectResult.LimitReached -> CreateError.LimitReached
                        CreateProjectResult.NoConnection -> CreateError.NoConnection
                        CreateProjectResult.ServerError -> CreateError.Server
                    },
                )
            }
            if (result is CreateProjectResult.Created) _created.send(result.projectId)
        }
    }

    private val _device = MutableStateFlow(AddDeviceUiState())
    val device: StateFlow<AddDeviceUiState> = _device.asStateFlow()

    fun onSerialChange(value: String) {
        val serial = normalizeSerial(value)
        _device.update { if (serial == it.serial) it else it.copy(serial = serial, error = null, device = null) }
    }

    /** Serial lấy từ màn 18: điền vào ô và kiểm tra ngay. */
    fun onScanned(serial: String) {
        onSerialChange(serial)
        onCheck()
    }

    fun onCheck() {
        val current = _device.value
        if (current.checking) return
        if (current.serial.isEmpty()) {
            _device.update { it.copy(error = SerialError.Empty) }
            return
        }
        _device.update { it.copy(checking = true, error = null, device = null) }
        scope.launch {
            val result = repository.checkSerial(current.serial)
            _device.update {
                // Người dùng đã sửa serial trong lúc chờ: bỏ kết quả của serial cũ.
                if (it.serial != current.serial) return@update it.copy(checking = false)
                when (result) {
                    is SerialCheckResult.Valid -> it.copy(checking = false, device = result.device)
                    SerialCheckResult.NotFound -> it.copy(checking = false, error = SerialError.NotFound)
                    SerialCheckResult.OwnedByOther -> it.copy(checking = false, error = SerialError.OwnedByOther)
                    SerialCheckResult.NoConnection -> it.copy(checking = false, error = SerialError.NoConnection)
                    SerialCheckResult.ServerError -> it.copy(checking = false, error = SerialError.Server)
                }
            }
        }
    }
}
