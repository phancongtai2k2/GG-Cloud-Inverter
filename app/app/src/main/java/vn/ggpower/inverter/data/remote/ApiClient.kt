package vn.ggpower.inverter.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import vn.ggpower.inverter.BuildConfig
import vn.ggpower.inverter.data.auth.SessionStore
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Không tới được máy chủ (mất mạng, hết thời gian chờ). */
class NoConnectionException(cause: Throwable? = null) : IOException("no connection", cause)

/** Máy chủ trả lời lỗi. [code] là mã trong thân `{"code": "..."}` (hợp đồng API), [body] là thân đầy đủ. */
class ApiException(val status: Int, val code: String, val body: JsonObject) : IOException("HTTP $status $code")

class ApiResponse(val status: Int, val body: JsonElement?, val location: String?)

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "tokens")

/**
 * Giữ access token trong bộ nhớ và refresh token trên đĩa, mã hoá bằng khoá AES-GCM nằm trong Android Keystore
 * (khoá không rời khỏi thiết bị, không sao lưu được).
 */
@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile var accessToken: String? = null
        private set

    suspend fun save(access: String, refresh: String) {
        accessToken = access
        val sealed = seal(refresh)
        context.tokenDataStore.edit { it[REFRESH] = sealed }
    }

    suspend fun refreshToken(): String? {
        val sealed = context.tokenDataStore.data.first()[REFRESH] ?: return null
        return runCatching { open(sealed) }.getOrNull()
    }

    suspend fun clear() {
        accessToken = null
        context.tokenDataStore.edit { it.remove(REFRESH) }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun seal(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    private fun open(sealed: String): String {
        val data = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_BYTES))
        }
        return String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES))
    }

    private companion object {
        const val KEY_ALIAS = "ggp_refresh_token"
        const val IV_BYTES = 12
        val REFRESH = stringPreferencesKey("refresh")
    }
}

/**
 * Gọi API backend (`BuildConfig.API_BASE_URL`, đặt ở local.properties). Tự gắn token, tự đổi token khi hết hạn,
 * và giữ bản sao phản hồi GET trên đĩa: màn hình hiện ngay dữ liệu lần trước, lần gọi sau chỉ tải lại khi máy chủ
 * báo có thay đổi (ETag / 304).
 */
@Singleton
class ApiClient @Inject constructor(
    @ApplicationContext context: Context,
    private val tokens: TokenStore,
    private val session: SessionStore,
) {
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // Trần cho cả một lời gọi (kể cả tra tên miền, vốn không có giới hạn riêng): mạng treo thì báo lỗi, không quay mãi.
        // Tải tệp lên dùng giới hạn riêng dài hơn; kênh WebSocket không chịu giới hạn này sau khi đã nối.
        .callTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val uploadHttp: OkHttpClient = http.newBuilder().callTimeout(5, TimeUnit.MINUTES).writeTimeout(60, TimeUnit.SECONDS).build()
    private val cacheDir = File(context.cacheDir, "api").apply { mkdirs() }
    private val refreshLock = Mutex()

    // ---------- bộ nhớ đệm trên đĩa ----------

    private fun cacheFile(path: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
        return File(cacheDir, digest.joinToString("") { "%02x".format(it) }.take(40))
    }

    /** Bản đã lưu của [path] (nếu có), không gọi mạng. */
    suspend fun cached(path: String): JsonElement? = withContext(Dispatchers.IO) {
        runCatching {
            val f = cacheFile(path)
            if (!f.exists()) null else json.parseToJsonElement(f.readText().substringAfter('\n'))
        }.getOrNull()
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) { cacheDir.listFiles()?.forEach { it.delete() }; Unit }

    // ---------- lời gọi ----------

    /** GET có xác thực; cập nhật bản lưu. 304 trả lại bản đang lưu. */
    suspend fun get(path: String): JsonElement = withContext(Dispatchers.IO) {
        val file = cacheFile(path)
        val stored = runCatching { if (file.exists()) file.readText() else null }.getOrNull()
        val etag = stored?.substringBefore('\n')?.takeIf { it.isNotEmpty() }
        val response = execute("GET", path, null, auth = true, etag = etag)
        if (response.status == 304 && stored != null) return@withContext json.parseToJsonElement(stored.substringAfter('\n'))
        response.body ?: throw ApiException(response.status, "EMPTY", JsonObject(emptyMap()))
    }

    suspend fun post(path: String, body: JsonObject, auth: Boolean = true, headers: Map<String, String> = emptyMap()): ApiResponse =
        withContext(Dispatchers.IO) { execute("POST", path, body, auth, headers = headers) }

    suspend fun put(path: String, body: JsonObject): ApiResponse = withContext(Dispatchers.IO) { execute("PUT", path, body, auth = true) }

    suspend fun patch(path: String, body: JsonObject): ApiResponse = withContext(Dispatchers.IO) { execute("PATCH", path, body, auth = true) }

    suspend fun delete(path: String): ApiResponse = withContext(Dispatchers.IO) { execute("DELETE", path, null, auth = true) }

    /** Gửi nội dung tệp (ảnh, video) ở dạng thô; máy chủ tự nhận diện loại tệp. */
    suspend fun upload(method: String, path: String, bytes: ByteArray): ApiResponse =
        withContext(Dispatchers.IO) { execute(method, path, null, auth = true, raw = bytes) }

    /** Tải một tệp cần xác thực (ảnh đại diện) về [target]. Trả false nếu không có. */
    suspend fun download(path: String, target: File): Boolean = withContext(Dispatchers.IO) {
        if (tokens.accessToken == null && !refresh()) return@withContext false
        val request = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer ${tokens.accessToken}").build()
        try {
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext false
                target.outputStream().use { out -> r.body?.byteStream()?.copyTo(out) }
                true
            }
        } catch (e: IOException) {
            throw NoConnectionException(e)
        }
    }

    private suspend fun execute(
        method: String,
        path: String,
        body: JsonObject?,
        auth: Boolean,
        etag: String? = null,
        headers: Map<String, String> = emptyMap(),
        retried: Boolean = false,
        raw: ByteArray? = null,
    ): ApiResponse {
        if (auth && tokens.accessToken == null && !refresh()) throw ApiException(401, "UNAUTHENTICATED", JsonObject(emptyMap()))
        val request = Request.Builder().url(baseUrl + path).apply {
            val payload = raw?.toRequestBody(BINARY) ?: body?.toString()?.toRequestBody(JSON)
            method(method, payload ?: if (method == "DELETE" || method == "GET") null else "".toRequestBody(JSON))
            if (auth) header("Authorization", "Bearer ${tokens.accessToken}")
            if (etag != null) header("If-None-Match", etag)
            headers.forEach { (k, v) -> header(k, v) }
        }.build()

        val response = try {
            (if (raw != null) uploadHttp else http).newCall(request).execute()
        } catch (e: IOException) {
            throw NoConnectionException(e)
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401 && auth && !retried && refresh()) {
                return execute(method, path, body, auth, etag, headers, retried = true, raw = raw)
            }
            val parsed = if (text.isBlank()) null else runCatching { json.parseToJsonElement(text) }.getOrNull()
            if (r.code == 304) return ApiResponse(304, null, null)
            if (!r.isSuccessful) {
                val obj = (parsed as? JsonObject) ?: JsonObject(emptyMap())
                if (r.code == 401 && auth) signOutLocally()
                throw ApiException(r.code, obj.str("code") ?: "HTTP_${r.code}", obj)
            }
            if (method == "GET" && parsed != null) {
                runCatching { cacheFile(path).writeText((r.header("ETag") ?: "") + "\n" + text) }
            }
            return ApiResponse(r.code, parsed, r.header("Location"))
        }
    }

    /** Đổi refresh token lấy cặp token mới. Chỉ một lần đổi chạy tại một thời điểm. */
    private suspend fun refresh(): Boolean = refreshLock.withLock {
        val before = tokens.accessToken
        val refresh = tokens.refreshToken() ?: return false
        val request = Request.Builder().url("$baseUrl/api/v1/auth/refresh")
            .post(buildJsonObject { put("refresh_token", refresh) }.toString().toRequestBody(JSON)).build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw NoConnectionException(e)
        }
        response.use { r ->
            if (!r.isSuccessful) {
                // Lời gọi khác có thể vừa đổi xong trong lúc chờ khoá.
                if (tokens.accessToken != null && tokens.accessToken != before) return true
                if (r.code == 401) signOutLocally()
                return false
            }
            val obj = json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
            tokens.save(obj.str("access_token")!!, obj.str("refresh_token")!!)
            return true
        }
    }

    suspend fun signIn(tokenResponse: JsonObject) {
        tokens.save(tokenResponse.str("access_token")!!, tokenResponse.str("refresh_token")!!)
        session.setLoggedIn(true)
    }

    suspend fun signOutLocally() {
        tokens.clear()
        clearCache()
        session.setLoggedIn(false)
    }

    suspend fun refreshTokenForLogout(): String? = tokens.refreshToken()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val BINARY = "application/octet-stream".toMediaType()
    }
}
