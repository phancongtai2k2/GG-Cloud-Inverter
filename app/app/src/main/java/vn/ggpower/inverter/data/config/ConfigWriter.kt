package vn.ggpower.inverter.data.config

import kotlinx.coroutines.delay
import vn.ggpower.inverter.domain.config.ConfigCommand
import vn.ggpower.inverter.domain.config.ConfigRequest
import vn.ggpower.inverter.domain.config.ConfigWriteException
import vn.ggpower.inverter.domain.config.WriteErrorCode
import vn.ggpower.inverter.domain.config.WriteOutcome
import vn.ggpower.inverter.domain.config.outcome
import java.util.UUID

/** Kết quả của một lần ghi cấu hình (dùng chung màn 16 và 16b). */
sealed interface WriteResult {
    /** Lệnh đã tới trạng thái cuối (hoặc hỏi quá lâu → [WriteOutcome.Unknown]). */
    data class Finished(val outcome: WriteOutcome) : WriteResult
    /** Backend từ chối ngay (HTTP 4xx, không tạo lệnh). */
    data class Rejected(val code: WriteErrorCode, val retryAfterSeconds: Int?) : WriteResult
    /** Lỗi mạng / máy chủ khi gửi lệnh. */
    data class Failed(val noConnection: Boolean) : WriteResult
}

/**
 * Gửi một lệnh ghi và hỏi trạng thái tới khi xong (ADR-002 mục 15; hợp đồng `POST /config-commands`, `GET …/{id}`):
 * mỗi lần xác nhận dùng một `Idempotency-Key` mới; hỏi lại theo `poll_after_ms`; lỗi mạng khi hỏi thì thử tiếp,
 * quá [MAX_POLLS] lần coi như không rõ kết quả.
 */
class ConfigWriter(
    private val repository: ConfigRepository,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun write(projectId: String, request: ConfigRequest, basedOnVersion: String): WriteResult {
        val start = try {
            repository.submit(projectId, request, basedOnVersion, newKey())
        } catch (e: ConfigWriteException) {
            return WriteResult.Rejected(e.code, e.retryAfterSeconds)
        } catch (e: ConfigLoadException) {
            return WriteResult.Failed(noConnection = e.kind == ConfigLoadException.Kind.NoConnection)
        }
        val command = poll(projectId, start)
        return WriteResult.Finished(if (command.status.isFinal) command.outcome() else WriteOutcome.Unknown)
    }

    private suspend fun poll(projectId: String, start: ConfigCommand): ConfigCommand {
        var command = start
        var attempts = 0
        while (!command.status.isFinal) {
            if (++attempts > MAX_POLLS) return command
            delay(command.pollAfterMs ?: DEFAULT_POLL_MS)
            try {
                command = repository.command(projectId, command.id)
            } catch (_: ConfigLoadException) {
                // Giữ trạng thái cũ và hỏi lại ở vòng sau.
            }
        }
        return command
    }

    private companion object {
        const val DEFAULT_POLL_MS = 1_000L
        /** Lệnh hết hạn sau 30 s (hợp đồng); hỏi tối đa ~40 lần rồi coi như không rõ. */
        const val MAX_POLLS = 40
    }
}
