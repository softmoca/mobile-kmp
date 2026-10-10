package kr.jachwisunbae.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import kr.jachwisunbae.app.data.TokenStore

data class Coordinates(val latitude: Double, val longitude: Double)

data class MapMarker(val id: String, val title: String, val latitude: Double, val longitude: Double)

data class PickedPhoto(val bytes: ByteArray, val filename: String, val contentType: String)

interface PlatformServices {
    suspend fun pickPhoto(): PickedPhoto?
    suspend fun currentLocation(): Coordinates
    suspend fun previewPdf(bytes: ByteArray, filename: String)
    suspend fun sharePdf(bytes: ByteArray, filename: String)
    suspend fun savePdf(bytes: ByteArray, filename: String)
}

@Composable
expect fun rememberPlatformServices(): PlatformServices

@Composable
expect fun rememberTokenStore(): TokenStore

@Composable
expect fun NativeMap(
    markers: List<MapMarker>,
    selectedLocation: Coordinates?,
    onLocationSelected: (Coordinates) -> Unit,
    modifier: Modifier = Modifier,
)

expect fun decodePhoto(bytes: ByteArray): ImageBitmap
