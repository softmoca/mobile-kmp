package kr.jachwisunbae.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kr.jachwisunbae.app.data.*

internal val RoomOptions = linkedMapOf("AIR_CONDITIONER" to "에어컨", "REFRIGERATOR" to "냉장고", "WASHING_MACHINE" to "세탁기", "SINK" to "싱크대", "GAS_STOVE" to "가스레인지", "MICROWAVE" to "전자레인지", "SHOE_CABINET" to "신발장", "WARDROBE" to "옷장", "BED" to "침대", "DESK" to "책상", "TV" to "TV", "INDUCTION" to "인덕션")
internal val UtilityOptions = linkedMapOf("WATER" to "수도", "ELECTRICITY" to "전기", "GAS" to "가스", "INTERNET" to "인터넷")
internal fun CheckStage.label() = if (this == CheckStage.ON_SITE) "집에서 확인" else "계약 전"
internal fun CheckStatus.label() = when (this) { CheckStatus.GOOD -> "괜찮음"; CheckStatus.CAUTION -> "주의"; CheckStatus.UNCONFIRMED -> "미확인" }
internal fun CheckStatus.ink() = when (this) { CheckStatus.GOOD -> Color(0xFF3F6FC4); CheckStatus.CAUTION -> Color(0xFFB53C3C); CheckStatus.UNCONFIRMED -> Color(0xFF666666) }
internal fun progressLabel(progress: ProgressDto): String = when {
    progress.totalCount == 0 -> "아직 체크 항목이 없어요"
    progress.completedCount >= progress.totalCount -> "확인 완료 · ${progress.totalCount}개 항목"
    else -> "확인 ${progress.completedCount} / ${progress.totalCount}개"
}
internal fun MapCategory.label() = when (this) { MapCategory.HOSPITAL -> "병원"; MapCategory.TRANSPORT -> "버스정류장"; MapCategory.SCHOOL -> "학교"; MapCategory.CONVENIENCE -> "편의시설"; MapCategory.AGENCY -> "중개사" }
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
    fun replace(loaded: T) {
        requestVersion++
        active?.cancel()
        value = loaded; error = null; loading = false
    }
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

/** Refresh existing screen content without replacing its scroll position or editor state. */
@Composable
internal fun <T> rememberRefreshLoad(identity: Any?, revision: Int, loader: suspend () -> T): LoadState<T> {
    val scope = rememberCoroutineScope()
    val state = remember(identity) { LoadState(scope, loader) }
    LaunchedEffect(state, revision) { state.reload() }
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
    Scaffold(topBar = { TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) TextButton(onClick = onBack, modifier = Modifier.testTag("page-back")) { Text("‹ 뒤로") } }, actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0)) }, contentWindowInsets = WindowInsets(0, 0, 0, 0), content = content)
}

@Composable internal fun ScrollBody(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable internal fun <T> Resource(state: LoadState<T>, refreshFailureMessage: String? = null, content: @Composable (T) -> Unit) {
    when {
        state.value != null -> {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Notice("${refreshFailureMessage ?: "새로 불러오지 못했어요. 마지막으로 확인한 기록을 표시합니다."}\n$it", true)
                TextButton(onClick = state::reload) { Text("다시 시도") }
            }
            content(state.value!!)
        }
        state.loading -> Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Notice(state.error!!, error = true)
            Button(onClick = state::reload) { Text("다시 시도") }
        }
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
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null) Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable internal fun Progress(progress: ProgressDto) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(progressLabel(progress), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text("${progress.progressRate}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(progress = { (progress.progressRate / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        if (progress.totalCount > 0) Text("괜찮음 ${progress.goodCount} · 주의 ${progress.cautionCount} · 미확인 ${progress.unconfirmedCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun ConfirmDelete(title: String, text: String, busy: Boolean, onDismiss: () -> Unit, error: String? = null, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(text); error?.let { Notice(it, true) } } },
        confirmButton = { TextButton(enabled = !busy, onClick = onDelete, modifier = Modifier.testTag("confirm-delete")) { Text(if (busy) "삭제 중…" else "삭제", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("취소") } })
}
