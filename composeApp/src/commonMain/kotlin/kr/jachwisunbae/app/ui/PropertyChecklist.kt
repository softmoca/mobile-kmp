package kr.jachwisunbae.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.jachwisunbae.app.data.*

@Composable internal fun ChecklistOverview(api: ApiClient, propertyId: Long, revision: Int, onOpen: (CheckStage) -> Unit) {
    val overview = rememberLoad(propertyId, revision) { api.checklistOverview(propertyId) }
    Resource(overview) { result -> ScrollBody(PaddingValues()) {
        SectionTitle("한 번 더 살펴볼수록, 좋은 선택에 가까워져요.")
        Progress(result.overallProgress)
        CheckStage.entries.forEach { stage ->
            val summary = result.stages.firstOrNull { it.stage == stage }
            OutlinedCard(onClick = { onOpen(stage) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(stage.label(), if (stage == CheckStage.ON_SITE) "채광, 소음과 시설 상태를 직접 확인해요." else "서명 전 등기, 특약과 비용을 확인해요.")
                    Text(summary?.checklistName ?: "체크리스트를 선택해 시작하세요", style = MaterialTheme.typography.bodyMedium)
                    summary?.progress?.let { Progress(it) }
                    Text(if (summary?.applied == true) "체크 이어하기 ›" else "체크 시작하기 ›", fontWeight = FontWeight.Bold)
                }
            }
        }
    } }
}

private data class ChecklistContent(val overview: ChecklistOverviewDto, val applied: AppliedChecklistDto?)
private data class ChecklistChoice(val source: ChecklistSource, val id: Long?, val label: String)

@Composable internal fun PropertyChecklistScreen(api: ApiClient, propertyId: Long, stage: CheckStage, onBack: () -> Unit, onChanged: () -> Unit) {
    val content = rememberLoad(propertyId, stage) {
        val overview = api.checklistOverview(propertyId)
        val checklistId = overview.stages.firstOrNull { it.stage == stage }?.propertyChecklistId
        ChecklistContent(overview, checklistId?.let { api.appliedChecklist(propertyId, it) })
    }
    val templates = rememberLoad(stage) { api.userChecklists(stage) }
    val action = rememberAction()
    var chooser by remember { mutableStateOf(false) }
    var replacement by remember { mutableStateOf<ChecklistChoice?>(null) }
    var memoItem by remember { mutableStateOf<AppliedChecklistItemDto?>(null) }
    fun apply(choice: ChecklistChoice) {
        action.run("체크리스트를 적용했어요.") {
            api.applyChecklist(propertyId, stage, choice.source, choice.id)
            chooser = false; replacement = null; content.reload(); onChanged()
        }
    }
    Page(stage.label(), onBack, actions = { TextButton(enabled = !action.busy, onClick = { chooser = true }) { Text("목록 선택") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { ActionNotice(action) }
            Resource(content) { state ->
                val checklist = state.applied
                if (checklist == null) ScrollBody(PaddingValues()) {
                    EmptyState("확인할 항목을 준비해 볼까요?", "기본 목록 또는 나만의 체크리스트를 적용하세요.", "체크리스트 선택", { chooser = true })
                }
                else LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { SectionTitle(checklist.checklistName); Spacer(Modifier.height(12.dp)); state.overview.stages.firstOrNull { it.stage == stage }?.progress?.let { Progress(it) } }
                    if (checklist.items.isEmpty()) item { Notice("이 체크리스트에는 항목이 없어요. 다른 목록을 선택해 주세요.") }
                    items(checklist.items.sortedBy { it.displayOrder }, key = { it.id }) { item ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(item.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(CheckStatus.GOOD, CheckStatus.CAUTION, CheckStatus.UNCONFIRMED).forEach { status ->
                                        FilterChip(selected = item.status == status, enabled = !action.busy, onClick = {
                                            if (status != item.status) action.run { api.updateCheckStatus(propertyId, checklist.id, item.id, status); content.reload(); onChanged() }
                                        }, label = { Text(status.label(), style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.weight(1f))
                                    }
                                }
                                if (item.memo.isNotBlank()) Text(item.memo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(enabled = !action.busy, onClick = { memoItem = item }) { Text(if (item.memo.isBlank()) "+ 메모 추가" else "메모 수정") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (chooser) AlertDialog(onDismissRequest = { if (!action.busy) chooser = false }, title = { Text("${stage.label()} 체크리스트") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("같은 기본 항목의 체크 결과와 메모는 유지됩니다. 새 목록에서 제외되는 항목의 기록은 사라집니다.", style = MaterialTheme.typography.bodySmall)
            Resource(templates) { result ->
                LazyColumn(Modifier.heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedButton(enabled = !action.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                            val choice = ChecklistChoice(ChecklistSource.SYSTEM_DEFAULT, null, "시스템 기본 목록")
                            if (content.value?.applied != null) replacement = choice else apply(choice)
                        }) { Text("시스템 기본 목록") }
                    }
                    items(result.items, key = { it.id }) { template -> OutlinedButton(enabled = !action.busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        val choice = ChecklistChoice(ChecklistSource.USER, template.id, template.name)
                        if (content.value?.applied != null) replacement = choice else apply(choice)
                    }) { Text("${template.name} (${template.itemCount}개)") } }
                }
            }
            ActionNotice(action)
        }
    }, confirmButton = { TextButton(enabled = !action.busy, onClick = { chooser = false }) { Text("닫기") } })
    replacement?.let { choice -> AlertDialog(onDismissRequest = { if (!action.busy) replacement = null }, title = { Text("체크리스트를 교체할까요?") }, text = { Text("'${choice.label}'을 적용합니다. 같은 기본 항목의 기록은 유지되고, 새 목록에서 제외되는 항목의 체크 결과와 메모는 사라집니다.") },
        confirmButton = { TextButton(enabled = !action.busy, onClick = { apply(choice) }) { Text("교체") } }, dismissButton = { TextButton(enabled = !action.busy, onClick = { replacement = null }) { Text("취소") } }) }
    memoItem?.let { item ->
        var draft by rememberSaveable(item.id) { mutableStateOf(item.memo) }
        AlertDialog(onDismissRequest = { if (!action.busy) memoItem = null }, title = { Text("항목 메모") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.question, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(draft, { if (it.length <= 500) draft = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, label = { Text("메모") }, supportingText = { Text("${draft.length} / 500") })
                ActionNotice(action)
            }
        }, confirmButton = { TextButton(enabled = !action.busy, onClick = { content.value?.applied?.let { applied -> action.run("메모를 저장했어요.") {
            api.updateCheckMemo(propertyId, applied.id, item.id, draft); memoItem = null; content.reload(); onChanged()
        } } }) { Text("저장") } }, dismissButton = { TextButton(enabled = !action.busy, onClick = { memoItem = null }) { Text("취소") } })
    }
}
