package vn.ggpower.inverter.ui.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import vn.ggpower.inverter.data.telemetry.TelemetryLoadException
import java.time.Clock
import java.time.Instant

/** Trạng thái màn 17 cho số liệu kiểu [T]. Mọi nhóm dữ liệu hiện cùng lúc, không còn chọn nhóm (thiết kế mới). */
sealed interface LiveUiState<out T> {
    data object Loading : LiveUiState<Nothing>
    data class Error(val noConnection: Boolean) : LiveUiState<Nothing>

    /** [now]: đồng hồ cập nhật mỗi giây để dòng "cập nhật N giây trước" chạy. */
    data class Content<T>(val data: T, val now: Instant) : LiveUiState<T>
}

/**
 * Số liệu thời gian thực của một thiết bị (đặc tả §17.1, §17.5): chỉ nhận khi màn đang hiển thị (dừng sau 5 s không
 * còn ai theo dõi); nút làm mới mở lại luồng; lỗi khi đã có số liệu thì giữ số liệu cũ. Dùng chung inverter và pin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveController<T : Any>(
    private val source: () -> Flow<T>,
    private val clock: Clock,
    scope: CoroutineScope,
) {
    private sealed interface Feed<out T> {
        data class Data<T>(val value: T) : Feed<T>
        data class Failed(val noConnection: Boolean) : Feed<Nothing>
    }

    private val refreshes = MutableStateFlow(0)
    private var last: T? = null

    private val feed: Flow<Feed<T>> = refreshes.flatMapLatest {
        source()
            .map<T, Feed<T>> { Feed.Data(it) }
            .catch { e ->
                val kind = (e as? TelemetryLoadException)?.kind ?: throw e
                emit(Feed.Failed(kind == TelemetryLoadException.Kind.NoConnection))
            }
    }

    private val ticker = flow {
        while (true) {
            emit(clock.instant())
            delay(1_000)
        }
    }

    val state: StateFlow<LiveUiState<T>> =
        combine(feed, ticker) { f, now ->
            when (f) {
                is Feed.Data -> {
                    last = f.value
                    LiveUiState.Content(f.value, now)
                }
                is Feed.Failed -> last?.let { LiveUiState.Content(it, now) } ?: LiveUiState.Error(f.noConnection)
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), LiveUiState.Loading)

    fun onRefresh() = refreshes.update { it + 1 }
}
