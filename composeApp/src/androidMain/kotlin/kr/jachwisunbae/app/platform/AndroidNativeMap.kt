package kr.jachwisunbae.app.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil

private object MapNetworking {
    private var configured = false
    fun configure(context: android.content.Context) {
        if (configured) return
        configured = true
        HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "map-tiles-http"), 64L * 1024 * 1024))
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder()
                    .header("User-Agent", "${context.packageName}/1.0 (Android; JachwiSunbae property map)")
                    .build())
            }.build())
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
    }
}

private val osmStyle = """
    {"version":8,"name":"OpenStreetMap","sources":{"osm":{"type":"raster",
    "tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"minzoom":0,"maxzoom":19,
    "attribution":"© OpenStreetMap contributors"}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}
""".trimIndent()

@Composable
actual fun NativeMap(
    markers: List<MapMarker>,
    selectedLocation: Coordinates?,
    onLocationSelected: (Coordinates) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestSelection = rememberUpdatedState(onLocationSelected)
    val uriHandler = LocalUriHandler.current
    var readyMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapError by remember { mutableStateOf(false) }
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapNetworking.configure(context.applicationContext)
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { mapError = true }
            getMapAsync { map ->
                map.setPrefetchZoomDelta(0)
                map.setStyle(Style.Builder().fromJson(osmStyle)) {
                    mapError = false
                    readyMap = map
                }
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(37.5665, 126.9780), 14.0))
                map.addOnMapClickListener { point ->
                    latestSelection.value(Coordinates(point.latitude, point.longitude))
                    true
                }
                map.setOnMarkerClickListener { marker ->
                    latestSelection.value(Coordinates(marker.position.latitude, marker.position.longitude))
                    false
                }
            }
        }
    }
    val camera = remember { arrayOf<Coordinates?>(null) }
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(readyMap, markers, selectedLocation) {
        val map = readyMap ?: return@LaunchedEffect
        map.clear()
        markers.forEach { marker ->
            map.addMarker(MarkerOptions().position(LatLng(marker.latitude, marker.longitude)).title(marker.title))
        }
        selectedLocation?.let { location ->
            map.addMarker(MarkerOptions().position(LatLng(location.latitude, location.longitude)).title("선택한 위치"))
        }
        val center = selectedLocation ?: markers.firstOrNull()?.let { Coordinates(it.latitude, it.longitude) }
        if (center != null && center != camera[0]) {
            map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(center.latitude, center.longitude)))
            camera[0] = center
        }
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        if (mapError) Text("지도를 불러오지 못했습니다. 네트워크 연결을 확인해 주세요.",
            modifier = Modifier.align(Alignment.TopCenter).background(MaterialTheme.colorScheme.surface).padding(8.dp),
            style = MaterialTheme.typography.bodySmall)
        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .clickable { uriHandler.openUri("https://www.openstreetmap.org/copyright") }
                .padding(4.dp),
        )
    }
}
