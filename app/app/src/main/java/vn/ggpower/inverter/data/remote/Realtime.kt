package vn.ggpower.inverter.data.remote

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

// Đọc JSON: trường vắng hoặc null trả về null (thiết bị không đọc được thì không gửi — không coi là 0).
fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
fun JsonObject.num(name: String): Double? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject
fun JsonObject.arr(name: String): List<JsonObject> = (this[name] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
fun JsonElement.asObjects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
fun JsonObject.has(name: String): Boolean = this[name].let { it != null && it !is JsonNull }

/**
 * Kênh thời gian thực của một dự án (WebSocket). Máy chủ gửi ngay ảnh chụp trạng thái khi đăng ký, sau đó đẩy mỗi
 * thay đổi. Kênh chỉ mở khi có màn đang thu thập luồng (đặc tả §17.4) và tự nối lại, giãn dần tới 30 giây.
 */
@Singleton
class RealtimeClient @Inject constructor(private val api: ApiClient) {

    /** Các bản tin của máy chủ: `device` (trạng thái thiết bị), `alerts_changed`, `config_changed`. */
    fun messages(projectId: String): Flow<JsonObject> {
        var failures = 0
        return flow {
            emitAll(connect(projectId) { failures = 0 })
        }.retryWhen { cause, _ ->
            // Hết phiên đăng nhập thì dừng hẳn; lỗi mạng thì chờ rồi nối lại.
            if (cause is ApiException && cause.status == 401) return@retryWhen false
            failures++
            delay((1000L shl (failures - 1).coerceAtMost(5)).coerceAtMost(30_000L))
            true
        }
    }

    private fun connect(projectId: String, onOpen: () -> Unit): Flow<JsonObject> = callbackFlow {
        val ticket = api.post("/api/v1/realtime/ticket", buildJsonObject { }).body!!.jsonObject.str("ticket")!!
        val url = api.baseUrl.replaceFirst("http", "ws") + "/ws/v1/realtime?ticket=" + ticket
        val socket = api.http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onOpen()
                webSocket.send(buildJsonObject { put("subscribe", buildJsonArray { add(JsonPrimitive(projectId)) }) }.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { api.json.parseToJsonElement(text).jsonObject }.getOrNull()?.let { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(NoConnectionException(t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                close(NoConnectionException())
            }
        })
        awaitClose { socket.cancel() }
    }
}
