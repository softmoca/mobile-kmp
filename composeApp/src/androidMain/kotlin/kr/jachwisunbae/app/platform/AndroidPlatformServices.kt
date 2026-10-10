package kr.jachwisunbae.app.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private class PendingActions {
    var photo: Continuation<Uri?>? = null
    var permission: Continuation<Boolean>? = null
    var save: Continuation<Uri?>? = null
}

@Composable
actual fun rememberPlatformServices(): PlatformServices {
    val context = LocalContext.current
    val pending = remember { PendingActions() }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pending.photo?.resume(uri)
        pending.photo = null
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        pending.permission?.resume(result.values.any { it })
        pending.permission = null
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        pending.save?.resume(uri)
        pending.save = null
    }
    return remember(context) {
        object : PlatformServices {
            override suspend fun pickPhoto(): PickedPhoto? {
                val uri = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Uri?> { continuation ->
                        check(pending.photo == null) { "사진 선택이 이미 진행 중입니다." }
                        pending.photo = continuation
                        continuation.invokeOnCancellation { pending.photo = null }
                        photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                } ?: return null
                return withContext(Dispatchers.IO) { readPhoto(context, uri) }
            }

            override suspend fun currentLocation(): Coordinates = withContext(Dispatchers.Main) {
                val granted = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                    .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                if (!granted) {
                    val allowed = suspendCancellableCoroutine { continuation ->
                        check(pending.permission == null) { "위치 권한 요청이 이미 진행 중입니다." }
                        pending.permission = continuation
                        continuation.invokeOnCancellation { pending.permission = null }
                        permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                    check(allowed) { "위치 권한이 거부되었습니다. 설정에서 권한을 허용하거나 주소로 검색해 주세요." }
                }
                locate(context)
            }

            override suspend fun previewPdf(bytes: ByteArray, filename: String) {
                val file = writePdf(context, bytes, filename)
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent(context, PdfPreviewActivity::class.java).putExtra("pdf_path", file.absolutePath))
                }
            }

            override suspend fun sharePdf(bytes: ByteArray, filename: String) {
                val file = writePdf(context, bytes, filename)
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = android.content.ClipData.newRawUri("비교 PDF", uri)
                    }
                    context.startActivity(Intent.createChooser(intent, "비교 PDF 공유"))
                }
            }

            override suspend fun savePdf(bytes: ByteArray, filename: String) {
                val uri = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Uri?> { continuation ->
                        check(pending.save == null) { "파일 저장이 이미 진행 중입니다." }
                        pending.save = continuation
                        continuation.invokeOnCancellation { pending.save = null }
                        save.launch(safePdfName(filename))
                    }
                } ?: error("파일 저장을 취소했습니다.")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("선택한 위치에 파일을 저장하지 못했습니다.")
                }
            }
        }
    }
}

private fun readPhoto(context: Context, uri: Uri): PickedPhoto {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { stream ->
        val result = stream.readBytesWithLimit(5 * 1024 * 1024)
        check(result.isNotEmpty()) { "비어 있는 사진입니다." }
        result
    } ?: error("사진을 읽지 못했습니다.")
    val type = resolver.getType(uri)
    if (type == "image/jpeg" || type == "image/png") {
        return PickedPhoto(bytes, "photo-${UUID.randomUUID()}.${if (type == "image/png") "png" else "jpg"}", type)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("지원하지 않는 사진 형식입니다.")
    val converted = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        it.toByteArray()
    }
    bitmap.recycle()
    check(converted.size <= 5 * 1024 * 1024) { "변환된 사진이 5MB를 초과합니다. 작은 사진을 선택해 주세요." }
    return PickedPhoto(converted, "photo-${UUID.randomUUID()}.jpg", "image/jpeg")
}

private fun java.io.InputStream.readBytesWithLimit(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        check(output.size() + read <= limit) { "사진은 5MB 이하로 선택해 주세요." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

@Suppress("MissingPermission", "DEPRECATION")
private suspend fun locate(context: Context): Coordinates {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val provider = listOfNotNull(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER.takeIf { precise },
    ).firstOrNull { manager.isProviderEnabled(it) }
        ?: error("기기의 위치 서비스가 꺼져 있습니다. 설정에서 위치 서비스를 켜 주세요.")
    return try {
        withTimeout(20_000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resume(Coordinates(location.latitude, location.longitude))
                    }
                    override fun onProviderDisabled(provider: String) {
                        manager.removeUpdates(this)
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("위치 서비스를 사용할 수 없습니다."))
                    }
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        error("현재 위치를 가져오지 못했습니다. 야외에서 다시 시도하거나 주소로 검색해 주세요.")
    }
}

private fun safePdfName(filename: String) = filename.substringAfterLast('/').substringAfterLast('\\')
    .replace(Regex("[^a-zA-Z0-9가-힣._ -]"), "_").let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }

private suspend fun writePdf(context: Context, bytes: ByteArray, filename: String): File = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "pdf").apply { mkdirs() }
    directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000 }
        ?.forEach { it.delete() }
    File(directory, "${UUID.randomUUID()}-${safePdfName(filename)}").apply { writeBytes(bytes) }
}

actual fun decodePhoto(bytes: ByteArray): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "사진을 표시하지 못했습니다." }
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return (BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: error("사진을 표시하지 못했습니다.")).asImageBitmap()
}
