package kr.jachwisunbae.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import androidx.savedstate.read
import kotlinx.coroutines.launch
import kr.jachwisunbae.app.data.*
import kr.jachwisunbae.app.platform.PlatformServices

@Composable
fun App(api: ApiClient, platform: PlatformServices) {
    JachwiTheme {
        val session by api.session.collectAsState()
        val sessionScope = rememberCoroutineScope()
        LaunchedEffect(api) { api.bootstrapSession() }
        Surface(Modifier.fillMaxSize()) {
            when (val current = session) {
                AuthSession.Loading -> Column(Modifier.fillMaxSize().testTag("session-loading"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(); Spacer(Modifier.height(20.dp)); Text("기록을 불러올 준비를 하고 있어요.")
                }
                is AuthSession.LoggedOut -> SessionRecoveryScreen(current,
                    onRetry = { sessionScope.launch { api.bootstrapSession() } },
                    onStartNew = { sessionScope.launch { try { api.startNewSession() } catch (_: ApiException) { /* API publishes recovery state. */ } } })
                is AuthSession.Authenticated -> key(current.member.id) { AuthenticatedApp(api, platform, current.member) }
            }
        }
    }
}

@Composable private fun SessionRecoveryScreen(session: AuthSession.LoggedOut, onRetry: () -> Unit, onStartNew: () -> Unit) {
    var confirmNew by remember { mutableStateOf(false) }
    ScrollBody(PaddingValues()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
        Spacer(Modifier.height(36.dp))
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
            Text("집", Modifier.padding(horizontal = 24.dp, vertical = 18.dp), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        }
        Text("자취선배", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
        Text(if (session.requiresNewSessionConfirmation) "이전 기록을 이어서 불러오지 못했어요" else "기록을 불러오지 못했어요", style = MaterialTheme.typography.titleLarge)
        session.reason?.let { Notice(it, true) }
        if (session.requiresNewSessionConfirmation) {
            Text("이 앱에 남은 로그인 정보로 이전 기록에 접근할 수 없어요. 새로 시작하면 빈 기록으로 시작합니다.")
            if (session.canRetry) OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("session-retry")) { Text("다시 시도하기") }
            Button(onClick = { confirmNew = true }, modifier = Modifier.fillMaxWidth().testTag("session-start-new")) { Text("새 기록으로 시작하기") }
        } else {
            Text("연결을 확인한 뒤 다시 시도해 주세요. 일시적인 오류로 기존 기록을 새 기록으로 바꾸지 않습니다.")
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("session-retry")) { Text("다시 시도하기") }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }
    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, title = { Text("새 기록으로 시작할까요?") },
        text = { Text("새 익명 회원의 빈 기록으로 시작합니다. 이 앱에서 이전 회원의 기록을 이어서 볼 수 없으며, 기존 기록을 삭제하는 작업은 아닙니다.") },
        confirmButton = { TextButton(onClick = { confirmNew = false; onStartNew() }) { Text("새 기록 시작") } },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("취소") } })
}

@Composable private fun AuthenticatedApp(api: ApiClient, platform: PlatformServices, member: MemberDto) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: "properties"
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var revision by remember { mutableStateOf(0) }
    val roots = listOf(Triple("properties", "집", "내 매물"), Triple("map", "⌖", "지도"), Triple("templates", "✓", "체크리스트"), Triple("profile", "나", "마이"))
    fun navigate(route: String) { nav.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { nav.popBackStack() }
    val changed: () -> Unit = { revision++ }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, bottomBar = {
        if (roots.any { it.first == currentRoute }) NavigationBar {
            roots.forEach { (route, icon, label) -> NavigationBarItem(selected = route == currentRoute, onClick = {
                nav.navigate(route) { popUpTo("properties") { saveState = true }; launchSingleTop = true; restoreState = true }
            }, icon = { Text(icon, fontWeight = FontWeight.Bold) }, label = { Text(label) }) }
        }
    }) { insets ->
        NavHost(navController = nav, startDestination = "properties", modifier = Modifier.padding(insets).consumeWindowInsets(insets)) {
            composable("properties") {
                PropertyListScreen(api, revision, selected, onSelection = { id -> selected = if (id in selected) selected - id else if (selected.size < 5) selected + id else selected },
                    onOpen = { navigate("property/$it") }, onCreate = { navigate("property-create") }, onCompare = { navigate("compare") }, onAvailableIds = { available -> selected = selected.intersect(available) })
            }
            composable("property-create") { PropertyEditorScreen(api, null, platform, onBack = back, onSaved = { id -> changed(); nav.popBackStack(); navigate("property/$id") }) }
            composable("property/{id}") { route ->
                route.arguments?.read { getString("id") }?.toLongOrNull()?.let { id -> PropertyDetailScreen(api, platform, id, revision, onBack = back,
                    onEdit = { navigate("property/$id/edit") }, onChecklist = { navigate("property/$id/checklist/${it.name}") },
                    onDeleted = { selected = selected - id; changed(); back() }, onChanged = changed) }
            }
            composable("property/{id}/edit") { route ->
                route.arguments?.read { getString("id") }?.toLongOrNull()?.let { id -> PropertyEditorScreen(api, id, platform, back, onSaved = { changed(); back() }) }
            }
            composable("property/{id}/checklist/{stage}") { route ->
                val id = route.arguments?.read { getString("id") }?.toLongOrNull()
                val stage = route.arguments?.read { getString("stage") }?.let { runCatching { CheckStage.valueOf(it) }.getOrNull() }
                if (id != null && stage != null) PropertyChecklistScreen(api, id, stage, back, changed)
            }
            composable("compare") { ComparisonScreen(api, platform, selected.toList(), revision, onBack = back) }
            composable("map") { MapScreen(api, platform, revision, onOpen = { navigate("property/$it") }) }
            composable("templates") { TemplateListScreen(api, revision, onOpen = { navigate("template/$it") }, onCreate = { navigate("template-create") }) }
            composable("template-create") { TemplateEditorScreen(api, null, back, onSaved = { changed(); back() }) }
            composable("template/{id}") { route -> route.arguments?.read { getString("id") }?.toLongOrNull()?.let { id -> TemplateEditorScreen(api, id, back, onSaved = { changed(); back() }) } }
            composable("profile") { ProfileScreen(member, onNavigate = { route ->
                nav.navigate(route) { popUpTo("properties") { saveState = true }; launchSingleTop = true; restoreState = true }
            }) }
        }
    }
}

@Composable private fun ProfileScreen(member: MemberDto, onNavigate: (String) -> Unit) {
    Page("마이") { padding -> ScrollBody(padding) {
        Text("익명 회원", modifier = Modifier.testTag("anonymous-member"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("로그인 없이 이 앱에서 기록이 이어져요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Notice("다른 기기나 브라우저에서는 별도의 기록으로 시작합니다. 이 앱의 로그인 정보가 지워지거나 만료되면 이전 기록을 이어서 불러오지 못할 수 있어요.")
        SectionTitle("내 기록")
        listOf("properties" to "내 매물 관리", "templates" to "내 체크리스트 관리", "map" to "지도와 주변 시설").forEach { (route, title) ->
            OutlinedButton(onClick = { onNavigate(route) }, modifier = Modifier.fillMaxWidth()) { Text(title) }
        }
        SectionTitle("이렇게 사용해 보세요")
        Text("1. 내 매물에 후보를 등록해요.\n2. 집을 방문하며 체크 결과와 사진을 남겨요.\n3. 2~5개를 선택해 비교하고 PDF로 저장해요.", style = MaterialTheme.typography.bodyMedium)
        Text("회원 번호 ${member.id}", modifier = Modifier.testTag("anonymous-member-id"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}
