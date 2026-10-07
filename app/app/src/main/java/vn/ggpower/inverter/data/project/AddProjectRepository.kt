package vn.ggpower.inverter.data.project

import kotlinx.coroutines.delay
import vn.ggpower.inverter.domain.project.DeviceIdentity
import vn.ggpower.inverter.domain.project.ProjectInfoInput
import vn.ggpower.inverter.domain.project.ProjectStatus
import vn.ggpower.inverter.domain.project.ProjectSummary
import vn.ggpower.inverter.domain.project.Tariff
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Kết quả kiểm tra serial ở 14a (đặc tả §3.14 bước 1 và "Quy tắc"). */
sealed interface SerialCheckResult {
    data class Valid(val device: DeviceIdentity) : SerialCheckResult
    /** Không có trong danh sách xuất xưởng. */
    data object NotFound : SerialCheckResult
    /** "Thiết bị đang thuộc một tài khoản khác". */
    data object OwnedByOther : SerialCheckResult
    data object NoConnection : SerialCheckResult
    data object ServerError : SerialCheckResult
}

/** Dữ liệu tạo dự án (14a + 14b + 14c). [tariff] null khi bấm "Bỏ qua" ở 14c (cài sau). */
data class NewProject(
    val device: DeviceIdentity,
    val info: ProjectInfoInput,
    val pvKwp: Double,
    val tariff: Tariff?,
)

/** Kết quả tạo dự án (đặc tả §3.14 "Quy tắc"). */
sealed interface CreateProjectResult {
    data class Created(val projectId: String) : CreateProjectResult
    /** Đã đủ số dự án tối đa của tài khoản (mặc định 10). */
    data object LimitReached : CreateProjectResult
    data object NoConnection : CreateProjectResult
    data object ServerError : CreateProjectResult
}

/** Luồng 14 · Thêm dự án. Chưa có hợp đồng API (OPEN_QUESTIONS), dùng [FakeAddProjectRepository]. */
interface AddProjectRepository {
    suspend fun checkSerial(serial: String): SerialCheckResult

    suspend fun createProject(project: NewProject): CreateProjectResult
}

/**
 * Dữ liệu mẫu AddProject.dc.html: [SAMPLE_SERIAL] hợp lệ (GG SmartBox Hybrid 5 kW); [OWNED_SERIAL] đang thuộc tài khoản
 * khác; serial khác không có trong danh sách xuất xưởng.
 */
@Singleton
class FakeAddProjectRepository @Inject constructor(
    /** Dự án tạo xong được thêm vào danh sách màn 2 với trạng thái "Đang chờ kết nối" (đặc tả §3.14). */
    private val projects: FakeProjectRepository,
) : AddProjectRepository {
    /** Constructor cho test: danh sách dự án riêng. */
    constructor() : this(FakeProjectRepository())

    var failure: SerialCheckResult? = null
    var createFailure: CreateProjectResult? = null
    private var created = 0

    override suspend fun checkSerial(serial: String): SerialCheckResult {
        delay(FAKE_DELAY_MS)
        failure?.let { return it }
        return when (serial) {
            SAMPLE_SERIAL -> SerialCheckResult.Valid(SAMPLE_DEVICE)
            OWNED_SERIAL -> SerialCheckResult.OwnedByOther
            else -> SerialCheckResult.NotFound
        }
    }

    override suspend fun createProject(project: NewProject): CreateProjectResult {
        delay(FAKE_DELAY_MS)
        createFailure?.let { return it }
        if (projects.projects.size >= MAX_PROJECTS) return CreateProjectResult.LimitReached
        val id = "new-${++created}"
        projects.projects = projects.projects + ProjectSummary(
            id = id,
            name = project.info.name.trim(),
            address = project.info.address.trim(),
            configuration = project.device.name,
            siteType = project.info.siteType,
            status = ProjectStatus.WaitingConnection,
            pvPowerKw = null, todayEnergyKwh = null, batterySocPercent = null,
        )
        return CreateProjectResult.Created(id)
    }

    companion object {
        private const val FAKE_DELAY_MS = 600L
        const val SAMPLE_SERIAL = "SN2609A00137"
        const val OWNED_SERIAL = "SN2609A00999"
        /** Số dự án tối đa của một tài khoản (đặc tả §3.14). */
        const val MAX_PROJECTS = 10
        val SAMPLE_DEVICE = DeviceIdentity(SAMPLE_SERIAL, "GG SmartBox Hybrid 5 kW", "HYB5K", 5.0, LocalDate.of(2026, 8, 12))
    }
}
