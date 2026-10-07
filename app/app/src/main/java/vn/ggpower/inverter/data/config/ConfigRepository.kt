package vn.ggpower.inverter.data.config

import vn.ggpower.inverter.domain.config.ConfigCommand
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.ProjectConfig

/** Lỗi mạng / máy chủ khi đọc cấu hình hoặc hỏi trạng thái lệnh. Hợp đồng API còn là nháp nên chỉ phân loại. */
class ConfigLoadException(val kind: Kind) : Exception() {
    enum class Kind { NoConnection, Server }
}

/** Theo `docs/api/config-write.openapi.yaml` (nháp 0.2.0). */
interface ConfigRepository {
    /** Cấu hình hiện hành và quyền ghi của người đang xem (màn 16). Ném [ConfigLoadException] khi lỗi. */
    suspend fun getConfig(projectId: String): ProjectConfig

    /**
     * Gửi một lệnh ghi (`POST /config-commands`). [basedOnVersion]: `version` của cấu hình người dùng đã thấy khi xác
     * nhận; [idempotencyKey]: khoá mới cho mỗi lần xác nhận. Ném
     * [vn.ggpower.inverter.domain.config.ConfigWriteException] khi backend từ chối, [ConfigLoadException] khi lỗi mạng.
     */
    suspend fun submit(projectId: String, request: ConfigRequest, basedOnVersion: String, idempotencyKey: String): ConfigCommand

    /** Trạng thái hiện tại của một lệnh (`GET /config-commands/{id}`). */
    suspend fun command(projectId: String, commandId: String): ConfigCommand
}
