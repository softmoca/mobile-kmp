package kr.jachwisunbae.app.platform

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File

/** Uses the OS PDF renderer, so preview also works without an external PDF reader. */
class PdfPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val renderer = runCatching {
            val file = File(requireNotNull(intent.getStringExtra("pdf_path"))).canonicalFile
            require(file.parentFile == File(cacheDir, "pdf").canonicalFile)
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }.getOrNull()
        setContent {
            MaterialTheme {
                DisposableEffect(renderer) { onDispose { renderer?.close() } }
                Column(Modifier.safeDrawingPadding()) {
                    TextButton(onClick = { finish() }) { Text("닫기 · 비교 PDF") }
                    if (renderer == null) {
                        Text("PDF를 열지 못했습니다. 다시 다운로드해 주세요.", Modifier.padding(20.dp))
                    } else {
                        LazyColumn {
                            items((0 until renderer.pageCount).toList()) { index ->
                                val bitmap = remember(index) {
                                    renderer.openPage(index).use { page ->
                                        val width = 1080
                                        Bitmap.createBitmap(width, (width * page.height / page.width).coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
                                            eraseColor(Color.WHITE)
                                            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        }
                                    }
                                }
                                Image(bitmap.asImageBitmap(), "PDF ${index + 1}쪽", Modifier.fillMaxWidth().padding(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
