package kr.jachwisunbae.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
        LaunchedEffect(api) { api.restoreSession() }
        Surface(Modifier.fillMaxSize()) {
            when (val current = session) {
                AuthSession.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is AuthSession.LoggedOut -> LoginScreen(api, current.reason) { sessionScope.launch { api.restoreSession() } }
                is AuthSession.Authenticated -> AuthenticatedApp(api, platform, current.member)
            }
        }
    }
}

@Composable private fun LoginScreen(api: ApiClient, reason: String?, onRetrySession: () -> Unit) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val action = rememberAction()
    ScrollBody(PaddingValues()) {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
        Spacer(Modifier.height(36.dp))
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
            Text("집", Modifier.padding(horizontal = 24.dp, vertical = 18.dp), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        }
        Text("자취선배", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold)
        Text("좋은 집을 고르는 나만의 기준", style = MaterialTheme.typography.titleMedium)
        Text("마음에 드는 집을 모으고, 직접 확인한 내용을 기록하고 비교해 보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        if (reason != null) Notice(reason, true)
        if (reason != null) TextButton(enabled = !action.busy, onClick = onRetrySession) { Text("저장된 로그인 다시 확인") }
        OutlinedTextField(value = nickname, onValueChange = { nickname = it.take(30) }, label = { Text("닉네임") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("login-nickname"), enabled = !action.busy)
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("비밀번호 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("login-password"), enabled = !action.busy,
            isError = password.isNotEmpty() && password.length !in 4..30,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "숨김" else "보기") } })
        Text("비밀번호를 설정한 닉네임은 같은 비밀번호를 입력해 주세요. 비밀번호는 4~30자입니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Notice("비밀번호 없이 시작하면 같은 닉네임을 아는 사람이 기록에 접근할 수 있어요.")
        ActionNotice(action)
        Button(onClick = { action.run { api.login(nickname.trim(), password.takeIf { it.isNotEmpty() }) } }, enabled = !action.busy && nickname.isNotBlank() && (password.isEmpty() || password.length in 4..30), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (action.busy) "로그인 중…" else "시작하기")
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }
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
        NavHost(navController = nav, startDestination = "properties", modifier = Modifier.padding(insets)) {
            composable("properties") {
                PropertyListScreen(api, revision, selected, onSelection = { id -> selected = if (id in selected) selected - id else if (selected.size < 5) selected + id else selected },
                    onOpen = { navigate("property/$it") }, onCreate = { navigate("property/new") }, onCompare = { navigate("compare") })
            }
            composable("property/new") { PropertyEditorScreen(api, null, platform, onBack = back, onSaved = { id -> changed(); nav.popBackStack(); navigate("property/$id") }) }
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
            composable("templates") { TemplateListScreen(api, revision, onOpen = { navigate("template/$it") }, onCreate = { navigate("template/new") }) }
            composable("template/new") { TemplateEditorScreen(api, null, back, onSaved = { changed(); back() }) }
            composable("template/{id}") { route -> route.arguments?.read { getString("id") }?.toLongOrNull()?.let { id -> TemplateEditorScreen(api, id, back, onSaved = { changed(); back() }) } }
            composable("profile") { ProfileScreen(api, member) }
        }
    }
}

@Composable private fun ProfileScreen(api: ApiClient, member: MemberDto) {
    val action = rememberAction()
    var confirm by remember { mutableStateOf(false) }
    Page("마이") { padding -> ScrollBody(padding) {
        Text("${member.name}님", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(if (member.passwordProtected) "비밀번호로 보호된 계정" else "비밀번호 없는 공유 기록 공간", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("차근차근, 나에게 맞는 집을 찾아보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("자취선배 모바일")
        Notice("매물과 체크리스트는 기존 자취선배 계정에 저장됩니다. 로그아웃하면 이 기기의 로그인 정보가 삭제됩니다.")
        ActionNotice(action)
        OutlinedButton(onClick = { confirm = true }, enabled = !action.busy, modifier = Modifier.fillMaxWidth()) { Text("로그아웃") }
    } }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("로그아웃할까요?") }, text = { Text("저장된 매물은 유지되며 다음에 다시 로그인할 수 있어요.") },
        confirmButton = { TextButton(onClick = { confirm = false; action.run { api.logout() } }) { Text("로그아웃") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } })
}
