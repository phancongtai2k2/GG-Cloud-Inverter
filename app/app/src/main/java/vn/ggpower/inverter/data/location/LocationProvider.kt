package vn.ggpower.inverter.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import vn.ggpower.inverter.domain.project.Coordinates
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Kết quả lấy vị trí ở 14b. [address] là địa chỉ tra từ toạ độ (có thể null khi máy không tra được). */
sealed interface LocationResult {
    data class Found(val coordinates: Coordinates, val address: String?) : LocationResult
    /** Dịch vụ vị trí của máy đang tắt. */
    data object Disabled : LocationResult
    data object Failed : LocationResult
}

/** Lấy vị trí hiện tại một lần. Gọi sau khi đã có quyền vị trí (màn hình tự xin quyền). */
interface LocationProvider {
    suspend fun currentLocation(): LocationResult
}

/**
 * Dùng dịch vụ vị trí có sẵn của Android (không dùng Google Play Services — quyết 03/10/2026). Chỉ cần quyền vị trí
 * gần đúng (COARSE): toạ độ dùng tra cứu thời tiết. Chờ tối đa [TIMEOUT_MS].
 */
@Singleton
class AndroidLocationProvider @Inject constructor(@ApplicationContext private val context: Context) : LocationProvider {

    @SuppressLint("MissingPermission") // Màn 14b chỉ gọi sau khi người dùng đã cấp quyền.
    override suspend fun currentLocation(): LocationResult {
        val manager = context.getSystemService(LocationManager::class.java) ?: return LocationResult.Failed
        if (!LocationManagerCompat.isLocationEnabled(manager)) return LocationResult.Disabled
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return LocationResult.Disabled
        }
        val location = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) {
                        if (cont.isActive) cont.resume(it)
                    }
                }
            } ?: manager.getLastKnownLocation(provider)
        } catch (_: SecurityException) {
            null
        } ?: return LocationResult.Failed
        val coordinates = Coordinates(location.latitude, location.longitude)
        return LocationResult.Found(coordinates, reverseGeocode(coordinates))
    }

    /** Địa chỉ dạng "Xuân Quan, Văn Giang, Hưng Yên" (bỏ quốc gia); null khi máy không có Geocoder hoặc lỗi mạng. */
    private suspend fun reverseGeocode(c: Coordinates): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("vi"))
        val address: Address? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(c.latitude, c.longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses.firstOrNull()) }
                            override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                        })
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(c.latitude, c.longitude, 1)?.firstOrNull()
                }
            }
        } catch (_: Exception) {
            null
        }
        return address?.let { a ->
            listOfNotNull(a.subLocality ?: a.locality, a.subAdminArea, a.adminArea).distinct().joinToString(", ").ifBlank { null }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
