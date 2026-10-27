package kr.jachwisunbae.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.jachwisunbae.app.data.*

@Composable internal fun TemplateListScreen(api: ApiClient, revision: Int, onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    val templates = rememberRefreshLoad("templates", revision) { api.userChecklists() }
    Page("체크리스트", actions = { TextButton(onClick = templates::reload) { Text("새로고침") }; TextButton(onClick = onCreate) { Text("+ 만들기") } }) { padding ->
        Resource(templates) { result -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SectionTitle("나에게 필요한 항목만", "기본 항목을 골라 집을 볼 때마다 사용할 나만의 목록을 만드세요.") }
            if (result.items.isEmpty()) item { EmptyState("나만의 목록을 만들어 보세요", "현장 방문과 계약 전, 단계별로 준비할 수 있어요.", "체크리스트 만들기", onCreate) }
            items(result.items, key = { it.id }) { template -> OutlinedCard(onClick = { onOpen(template.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(template.stage.label(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("확인할 항목 ${template.itemCount}개  ·  편집하기 ›", style = MaterialTheme.typography.bodyMedium)
                }
            } }
        } }
    }
}

@Composable internal fun TemplateEditorScreen(api: ApiClient, id: Long?, onBack: () -> Unit, onSaved: () -> Unit) {
    val initial = rememberLoad(id) { id?.let { api.userChecklist(it) } }
    Page(if (id == null) "체크리스트 만들기" else "체크리스트 편집", onBack) { padding ->
        if (id == null) TemplateForm(api, null, padding, onSaved)
        else Resource(initial) { TemplateForm(api, it, padding, onSaved) }
    }
}

@Composable private fun TemplateForm(api: ApiClient, initial: UserChecklistDto?, padding: PaddingValues, onSaved: () -> Unit) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var stage by remember(initial?.id) { mutableStateOf(initial?.stage ?: CheckStage.ON_SITE) }
    var selected by remember(initial?.id) { mutableStateOf(initial?.items?.sortedBy { it.displayOrder }?.mapNotNull { it.systemCheckItemId }.orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    val system = rememberLoad(stage) { api.systemCheckItems(stage) }
    var initializedStage by remember { mutableStateOf<CheckStage?>(null) }
    LaunchedEffect(stage, system.value) {
        if (initial == null && system.value != null && initializedStage != stage) {
            selected = system.value.orEmpty().filter { it.itemType == "CORE" }.map { it.id }
            initializedStage = stage
        }
    }
    val action = rememberAction()
    val hasLegacyItems = initial?.items?.any { it.systemCheckItemId == null } == true
    var confirmDelete by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(name, { name = it.take(30) }, label = { Text("체크리스트 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${name.length} / 30") }) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CheckStage.entries.forEach { option -> FilterChip(selected = stage == option, enabled = initial == null, onClick = { stage = option; selected = emptyList() }, label = { Text(option.label()) }) }
        } }
        if (hasLegacyItems) item { Notice("직접 작성된 이전 항목이 포함된 목록은 현재 편집할 수 없어요. 기존 기록을 보존하려면 새 체크리스트를 만들어 주세요.") }
        item { SectionTitle("선택한 항목 ${selected.size} / 30개", "위·아래 버튼으로 확인할 순서를 정할 수 있어요.") }
        items(selected, key = { "selected:$it" }) { itemId ->
            val item = system.value?.firstOrNull { it.id == itemId }
            val question = item?.question ?: initial?.items?.firstOrNull { it.systemCheckItemId == itemId }?.question.orEmpty()
            val index = selected.indexOf(itemId)
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("${index + 1}. $question", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = index > 0 && !action.busy, onClick = { selected = selected.toMutableList().apply { removeAt(index); add(index - 1, itemId) } }) { Text("↑ 위") }
                    TextButton(enabled = index < selected.lastIndex && !action.busy, onClick = { selected = selected.toMutableList().apply { removeAt(index); add(index + 1, itemId) } }) { Text("↓ 아래") }
                    TextButton(enabled = !action.busy, onClick = { selected = selected - itemId }) { Text("제외") }
                }
            } }
        }
        item {
            HorizontalDivider(); Spacer(Modifier.height(12.dp)); SectionTitle("기본 체크 항목")
            Spacer(Modifier.height(8.dp)); OutlinedTextField(query, { query = it }, label = { Text("항목 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            if (system.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            system.error?.let { Notice(it, true); TextButton(onClick = system::reload) { Text("항목 다시 불러오기") } }
            if (system.value?.isEmpty() == true) Notice("이 단계의 기본 체크 항목이 없어요.")
        }
        items(system.value.orEmpty().filter { query.isBlank() || it.question.contains(query, true) }, key = { "system:${it.id}" }) { item ->
            Row(Modifier.fillMaxWidth().clickable(enabled = !action.busy) { selected = if (item.id in selected) selected - item.id else selected + item.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.id in selected, enabled = !action.busy, onCheckedChange = { selected = if (it) selected + item.id else selected - item.id })
                Column(Modifier.weight(1f)) {
                    Text(item.question, style = MaterialTheme.typography.bodyMedium)
                    Text(if (item.itemType == "CORE") "핵심 항목" else "선택 항목", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            validation?.let { Notice(it, true); Spacer(Modifier.height(8.dp)) }
            ActionNotice(action)
            Button(enabled = !action.busy && !system.loading && !hasLegacyItems, modifier = Modifier.fillMaxWidth().height(52.dp), onClick = {
                if (name.isBlank() || selected.size !in 1..30) validation = "이름과 1~30개의 체크 항목을 선택해 주세요."
                else {
                    validation = null
                    action.run {
                        if (initial == null) api.createUserChecklist(name.trim(), stage, selected)
                        else api.updateUserChecklist(initial.id, name.trim(), selected)
                        onSaved()
                    }
                }
            }) { Text(if (action.busy) "저장 중…" else "체크리스트 저장") }
            if (initial != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(enabled = !action.busy, modifier = Modifier.fillMaxWidth(), onClick = { confirmDelete = true }) { Text("체크리스트 삭제", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete && initial != null) ConfirmDelete("체크리스트를 삭제할까요?", "삭제한 목록은 새 매물에 적용할 수 없어요. 기존 매물에 적용된 기록은 유지됩니다.", action.busy, { confirmDelete = false }, error = action.error) {
        action.run { api.deleteUserChecklist(initial.id); confirmDelete = false; onSaved() }
    }
}
