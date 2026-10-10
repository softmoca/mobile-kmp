package kr.jachwisunbae.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.jachwisunbae.app.data.*

internal val RoomOptions = linkedMapOf("AIR_CONDITIONER" to "에어컨", "REFRIGERATOR" to "냉장고", "WASHING_MACHINE" to "세탁기", "SINK" to "싱크대", "GAS_STOVE" to "가스레인지", "MICROWAVE" to "전자레인지", "SHOE_CABINET" to "신발장", "WARDROBE" to "옷장", "BED" to "침대", "DESK" to "책상", "TV" to "TV", "INDUCTION" to "인덕션")
internal val UtilityOptions = linkedMapOf("WATER" to "수도", "ELECTRICITY" to "전기", "GAS" to "가스", "INTERNET" to "인터넷")
internal fun CheckStage.label() = if (this == CheckStage.ON_SITE) "집에서 확인" else "계약 전"
internal fun CheckStatus.label() = when (this) { CheckStatus.GOOD -> "좋아요"; CheckStatus.CAUTION -> "주의"; CheckStatus.UNCONFIRMED -> "미확인" }
internal fun MapCategory.label() = when (this) { MapCategory.HOSPITAL -> "병원"; MapCategory.TRANSPORT -> "교통"; MapCategory.SCHOOL -> "학교"; MapCategory.CONVENIENCE -> "편의시설"; MapCategory.AGENCY -> "중개사" }
internal fun money(amount: Long?): String = amount?.let { value -> value.toString().reversed().chunked(3).joinToString(",").reversed() + "원" } ?: "미입력"
internal fun Throwable.userMessage(): String = when (this) {
    is ApiException -> (listOf(message) + errors.map { it.reason }).distinct().joinToString("\n")
    is IllegalStateException -> message ?: "기능을 실행하지 못했어요. 다시 시도해 주세요."
    else -> "요청을 완료하지 못했어요. 네트워크 연결과 권한을 확인한 후 다시 시도해 주세요."
}

@Stable
internal class LoadState<T>(private val scope: CoroutineScope, private val loader: suspend () -> T) {
    private var active: Job? = null
    private var requestVersion = 0
    var value by mutableStateOf<T?>(null); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    fun reload() {
        val version = ++requestVersion
        active?.cancel()
        loading = true; error = null
        active = scope.launch {
            try { val loaded = loader(); if (version == requestVersion) value = loaded }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { if (version == requestVersion) error = failure.userMessage() }
            finally { if (version == requestVersion) loading = false }
        }
    }
}

@Composable
internal fun <T> rememberLoad(vararg keys: Any?, loader: suspend () -> T): LoadState<T> {
    val scope = rememberCoroutineScope()
    val state = remember(*keys) { LoadState(scope, loader) }
    LaunchedEffect(state) { state.reload() }
    return state
}

@Stable
internal class ActionState(private val scope: CoroutineScope) {
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var success by mutableStateOf<String?>(null); private set
    fun clear() { error = null; success = null }
    fun run(message: String? = null, operation: suspend () -> Unit) {
        if (busy) return
        busy = true; clear()
        scope.launch {
            try { operation(); success = message } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Throwable) { error = failure.userMessage() } finally { busy = false }
        }
    }
}

@Composable internal fun rememberAction(): ActionState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ActionState(scope) }
}

@Composable internal fun JachwiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF333333), onPrimary = Color.White,
        primaryContainer = Color(0xFFF1F1F1), onPrimaryContainer = Color(0xFF212124),
        secondary = Color(0xFF5F6368), secondaryContainer = Color(0xFFF5E7B5),
        onSecondaryContainer = Color(0xFF423916), background = Color.White,
        surface = Color.White, surfaceVariant = Color(0xFFF7F7F7),
        onSurface = Color(0xFF212124), onSurfaceVariant = Color(0xFF5F6368),
        outline = Color(0xFF868B94), outlineVariant = Color(0xFFEAEBED),
        error = Color(0xFFA33A3A), errorContainer = Color(0xFFFFECEA)
    ), shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp)), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun Page(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("‹ 뒤로") } }, actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0)) }, contentWindowInsets = WindowInsets(0, 0, 0, 0), content = content)
}

@Composable internal fun ScrollBody(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable internal fun <T> Resource(state: LoadState<T>, content: @Composable (T) -> Unit) {
    when {
        state.loading -> Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Notice(state.error!!, error = true)
            Button(onClick = state::reload) { Text("다시 시도") }
        }
        state.value != null -> content(state.value!!)
    }
}

@Composable internal fun Notice(message: String, error: Boolean = false) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun ActionNotice(state: ActionState) {
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Notice(it, true) }
    state.success?.let { Notice(it) }
}

@Composable internal fun SectionTitle(title: String, detail: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable internal fun EmptyState(title: String, description: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null) Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable internal fun Progress(progress: ProgressDto) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("체크 ${progress.completedCount} / ${progress.totalCount}", style = MaterialTheme.typography.bodySmall)
            Text("${progress.progressRate}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { (progress.progressRate / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable internal fun ConfirmDelete(title: String, text: String, busy: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(enabled = !busy, onClick = onDelete) { Text(if (busy) "삭제 중…" else "삭제", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("취소") } })
}
