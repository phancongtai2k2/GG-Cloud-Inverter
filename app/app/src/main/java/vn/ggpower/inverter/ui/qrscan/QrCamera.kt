package vn.ggpower.inverter.ui.qrscan

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.awaitCancellation

/**
 * Khung camera sau đọc mã QR liên tục (CameraX + ML Kit bản nhúng, chạy offline — quyết 03/10/2026). Chỉ gọi khi đã có
 * quyền camera. [onCode] nhận chữ thô của mã QR đầu tiên trong mỗi khung hình; [torchOn] bật đèn pin nếu máy có.
 * [onTorchAvailable] báo máy có đèn pin hay không (ẩn nút khi không có).
 */
@Composable
fun QrCamera(
    onCode: (String) -> Unit,
    torchOn: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode by rememberUpdatedState(onCode)
    val latestTorchAvailable by rememberUpdatedState(onTorchAvailable)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
            setAnalyzer(executor, MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
                result.getValue(scanner)?.firstOrNull()?.rawValue?.let { latestOnCode(it) }
            })
        }
        try {
            provider.unbindAll()
            val bound = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            camera = bound
            latestTorchAvailable(bound.cameraInfo.hasFlashUnit())
            awaitCancellation()
        } catch (_: IllegalArgumentException) {
            // Máy không có camera sau: giữ nền tối, người dùng nhập serial tay.
            latestTorchAvailable(false)
        } finally {
            provider.unbindAll()
            scanner.close()
            camera = null
        }
    }

    LaunchedEffect(camera, torchOn) {
        camera?.let { if (it.cameraInfo.hasFlashUnit()) it.cameraControl.enableTorch(torchOn) }
    }

    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier.fillMaxSize()) }
}
