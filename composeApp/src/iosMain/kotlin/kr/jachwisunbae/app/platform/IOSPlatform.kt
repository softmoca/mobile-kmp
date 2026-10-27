@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package kr.jachwisunbae.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.interop.UIKitView
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kr.jachwisunbae.app.data.TokenStore
import org.jetbrains.skia.Image
import platform.UIKit.UIView

/** A missing item and an unavailable Keychain must never imply the same anonymous identity. */
class SecureStorageReadResult(val value: String?, val error: String?)

/** Swift owns Apple delegates and native view lifetimes; Compose owns app state. */
interface IOSPlatformBridge {
    fun readToken(): SecureStorageReadResult
    fun writeToken(token: String): String?
    fun clearToken(): String?
    fun thumbnail(bytes: ByteArray): ByteArray?
    fun pickPhoto(onResult: (PickedPhoto?, String?) -> Unit)
    fun requestLocation(onResult: (Coordinates?, String?) -> Unit)
    fun previewPdf(bytes: ByteArray, filename: String, onResult: (String?) -> Unit)
    fun sharePdf(bytes: ByteArray, filename: String, onResult: (String?) -> Unit)
    fun savePdf(bytes: ByteArray, filename: String, onResult: (String?) -> Unit)
    fun createMap(): UIView
    fun updateMap(view: UIView, markers: List<MapMarker>, selectedLocation: Coordinates?, onLocationSelected: (Coordinates) -> Unit)
}

private lateinit var bridge: IOSPlatformBridge

fun configureIOSPlatform(implementation: IOSPlatformBridge) {
    bridge = implementation
}

@Composable
actual fun rememberTokenStore(): TokenStore = remember {
    object : TokenStore {
        override suspend fun read(): String? = withContext(Dispatchers.Default) {
            val result = bridge.readToken()
            result.error?.let { error(it) }
            result.value
        }
        override suspend fun write(token: String): Unit = withContext(Dispatchers.Default) {
            bridge.writeToken(token)?.let { error(it) }
        }
        override suspend fun clear(): Unit = withContext(Dispatchers.Default) {
            bridge.clearToken()?.let { error(it) }
        }
    }
}

@Composable
actual fun rememberPlatformServices(): PlatformServices = remember {
    object : PlatformServices {
        override suspend fun pickPhoto(): PickedPhoto? = suspendCancellableCoroutine { continuation ->
            bridge.pickPhoto { photo, error ->
                if (continuation.isActive) {
                    if (error != null) continuation.resumeWithException(IllegalStateException(error)) else continuation.resume(photo)
                }
            }
        }
        override suspend fun currentLocation(): Coordinates = suspendCancellableCoroutine { continuation ->
            bridge.requestLocation { location, error ->
                if (continuation.isActive) {
                    if (location != null) continuation.resume(location)
                    else continuation.resumeWithException(IllegalStateException(error ?: "현재 위치를 가져오지 못했습니다."))
                }
            }
        }
        override suspend fun previewPdf(bytes: ByteArray, filename: String) = action { bridge.previewPdf(bytes, filename, it) }
        override suspend fun sharePdf(bytes: ByteArray, filename: String) = action { bridge.sharePdf(bytes, filename, it) }
        override suspend fun savePdf(bytes: ByteArray, filename: String) = action { bridge.savePdf(bytes, filename, it) }
    }
}

private suspend fun action(launch: ((String?) -> Unit) -> Unit): Unit = suspendCancellableCoroutine { continuation ->
    launch { error ->
        if (continuation.isActive) {
            if (error != null) continuation.resumeWithException(IllegalStateException(error)) else continuation.resume(Unit)
        }
    }
}

@Composable
actual fun NativeMap(
    markers: List<MapMarker>,
    selectedLocation: Coordinates?,
    onLocationSelected: (Coordinates) -> Unit,
    modifier: Modifier,
) {
    UIKitView(factory = { bridge.createMap() }, modifier = modifier, accessibilityEnabled = true,
        update = { bridge.updateMap(it, markers, selectedLocation, onLocationSelected) })
}

actual fun decodePhoto(bytes: ByteArray): ImageBitmap {
    val thumbnail = bridge.thumbnail(bytes) ?: error("사진을 표시하지 못했습니다.")
    return Image.makeFromEncoded(thumbnail).toComposeImageBitmap()
}
