package kr.jachwisunbae.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.jachwisunbae.app.data.*
import kr.jachwisunbae.app.platform.*

@Composable internal fun PropertyListScreen(api: ApiClient, revision: Int, selected: Set<Long>, onSelection: (Long) -> Unit, onOpen: (Long) -> Unit, onCreate: () -> Unit, onCompare: () -> Unit) {
    val properties = rememberLoad(revision) { api.properties() }
    var query by rememberSaveable { mutableStateOf("") }
    Page("내 매물", actions = { TextButton(onClick = properties::reload) { Text("새로고침") }; TextButton(onClick = onCreate) { Text("+ 등록") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(query, { query = it }, label = { Text("이름 또는 주소 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            Resource(properties) { result ->
                val visible = result.items.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.address.orEmpty().contains(query, ignoreCase = true) }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text("${result.totalCount}개의 기록 · 비교할 매물을 2~5개 선택하세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (result.items.isEmpty()) item { EmptyState("첫 번째 집을 기록해 보세요", "방을 보러 가기 전 기본 정보를 남겨두세요.", "매물 등록", onCreate) }
                    else if (visible.isEmpty()) item { EmptyState("검색 결과가 없어요", "다른 이름이나 주소로 검색해 보세요.") }
                    items(visible, key = { it.id }) { property ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.clickable { onOpen(property.id) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(property.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(property.address ?: "주소 미입력", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Checkbox(checked = property.id in selected, enabled = property.id in selected || selected.size < 5, onCheckedChange = { onSelection(property.id) })
                                }
                                property.representativePhoto?.let { PhotoImage(api, property.id, it.id, property.name, Modifier.fillMaxWidth().height(150.dp)) }
                                Text("보증금 ${money(property.depositAmount)}  /  월세 ${money(property.monthlyRentAmount)}", style = MaterialTheme.typography.bodyMedium)
                                Progress(property.overallProgress)
                            }
                        }
                    }
                }
                if (selected.isNotEmpty()) Surface(shadowElevation = 6.dp) {
                    Button(onClick = onCompare, enabled = selected.size in 2..5, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("${selected.size}개 매물 비교${if (selected.size == 1) " · 1개 더 선택" else "하기"}") }
                }
            }
        }
    }
}

@Composable internal fun PropertyDetailScreen(api: ApiClient, platform: PlatformServices, id: Long, revision: Int, onBack: () -> Unit, onEdit: () -> Unit, onChecklist: (CheckStage) -> Unit, onDeleted: () -> Unit, onChanged: () -> Unit) {
    val property = rememberLoad(id, revision) { api.property(id) }
    val action = rememberAction()
    var tab by rememberSaveable { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    Page(property.value?.name ?: "매물 상세", onBack, actions = { TextButton(onClick = onEdit) { Text("수정") } }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) { listOf("기본 정보", "체크", "사진", "메모").forEachIndexed { index, label -> Tab(selected = index == tab, onClick = { tab = index }, text = { Text(label) }) } }
            Resource(property) { detail ->
                when (tab) {
                    0 -> ScrollBody(PaddingValues()) {
                        Text(detail.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(detail.address ?: "주소가 등록되지 않았어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfoRow("보증금", money(detail.depositAmount)); InfoRow("월세", money(detail.monthlyRentAmount)); InfoRow("관리비", money(detail.maintenanceFeeAmount))
                            InfoRow("관리비 포함", detail.utilityOptions.mapNotNull { UtilityOptions[it] }.joinToString(", ").ifBlank { "미입력" })
                        } }
                        InfoRow("입주 가능일", detail.availableMoveInDate ?: "미입력")
                        InfoRow("방문 예정", detail.visitScheduledAt?.replace('T', ' ') ?: "미입력")
                        InfoRow("발견 경로", detail.discoverySource?.ifBlank { "미입력" } ?: "미입력")
                        InfoRow("방 옵션", detail.roomOptions.mapNotNull { RoomOptions[it] }.joinToString(", ").ifBlank { "미입력" })
                        Progress(detail.overallProgress)
                        if (detail.latitude != null && detail.longitude != null) {
                            SectionTitle("매물 위치")
                            NativeMap(markers = listOf(MapMarker(id.toString(), detail.name, detail.latitude, detail.longitude)), selectedLocation = Coordinates(detail.latitude, detail.longitude), onLocationSelected = {}, modifier = Modifier.fillMaxWidth().height(230.dp))
                        }
                        ActionNotice(action)
                        OutlinedButton(onClick = { confirmDelete = true }, enabled = !action.busy, modifier = Modifier.fillMaxWidth()) { Text("매물 삭제", color = MaterialTheme.colorScheme.error) }
                    }
                    1 -> ChecklistOverview(api, id, revision, onChecklist)
                    2 -> PropertyPhotos(api, platform, id, onChanged)
                    3 -> PropertyMemo(api, id)
                }
            }
        }
    }
    if (confirmDelete) ConfirmDelete("매물을 삭제할까요?", "매물의 사진, 체크 결과와 메모도 함께 삭제됩니다. 복구할 수 없어요.", action.busy, { confirmDelete = false }) {
        action.run { api.deleteProperty(id); confirmDelete = false; onDeleted() }
    }
}

@Composable internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.width(86.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun PhotoImage(api: ApiClient, propertyId: Long, photoId: Long, label: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val image = rememberLoad(propertyId, photoId) {
        val bytes = api.photoBytes(propertyId, photoId)
        withContext(Dispatchers.Default) { decodePhoto(bytes) }
    }
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        when {
            image.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
            image.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { TextButton(onClick = image::reload) { Text("사진 다시 불러오기") } }
            else -> image.value?.let { Image(it, label, Modifier.fillMaxSize(), contentScale = contentScale) }
        }
    }
}

@Composable private fun PropertyPhotos(api: ApiClient, platform: PlatformServices, propertyId: Long, onChanged: () -> Unit) {
    val photos = rememberLoad(propertyId) { api.photos(propertyId) }
    val action = rememberAction()
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var preview by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("방문 사진", "JPEG · PNG · WebP · HEIC / 장당 5MiB 이하, 최대 30장")
            ActionNotice(action)
            Button(enabled = !action.busy && (photos.value?.totalCount ?: 0) < 30, onClick = { action.run {
                val photo = platform.pickPhoto() ?: return@run
                api.uploadPhoto(propertyId, photo.bytes, photo.filename, photo.contentType)
                photos.reload(); onChanged()
            } }, modifier = Modifier.fillMaxWidth()) { Text("사진 추가") }
        }
        Resource(photos) { result ->
            LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (result.items.isEmpty()) item { EmptyState("아직 사진이 없어요", "채광, 시설, 창밖 풍경을 남겨보세요.") }
                items(result.items, key = { it.id }) { photo ->
                    OutlinedCard { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhotoImage(api, propertyId, photo.id, "매물 사진 · 눌러서 전체 보기", Modifier.fillMaxWidth().height(220.dp).clickable { preview = photo.id })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(enabled = !action.busy && !photo.representative, onClick = { action.run("대표 사진을 변경했어요.") { api.setRepresentativePhoto(propertyId, photo.id); photos.reload(); onChanged() } }) { Text(if (photo.representative) "✓ 대표 사진" else "대표로 지정") }
                            TextButton(enabled = !action.busy, onClick = { pendingDelete = photo.id }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                        }
                    } }
                }
            }
        }
    }
    pendingDelete?.let { photoId -> ConfirmDelete("사진을 삭제할까요?", "선택한 사진은 복구할 수 없어요.", action.busy, { pendingDelete = null }) {
        action.run("사진을 삭제했어요.") { api.deletePhoto(propertyId, photoId); pendingDelete = null; photos.reload(); onChanged() }
    } }
    preview?.let { photoId -> Dialog(onDismissRequest = { preview = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Page("사진 전체 보기", { preview = null }) { padding ->
                PhotoImage(api, propertyId, photoId, "매물 사진 전체", Modifier.fillMaxSize().padding(padding).padding(12.dp), ContentScale.Fit)
            }
        }
    } }
}

@Composable private fun PropertyMemo(api: ApiClient, propertyId: Long) {
    val memo = rememberLoad(propertyId) { api.memo(propertyId) }
    val action = rememberAction()
    Resource(memo) { document ->
        var draft by rememberSaveable(propertyId, document.freeMemo) { mutableStateOf(document.freeMemo) }
        ScrollBody(PaddingValues()) {
            SectionTitle("자유 메모", "방의 첫인상부터 중개사와 나눈 이야기까지 남겨보세요.")
            OutlinedTextField(draft, { if (it.length <= 2000) draft = it }, label = { Text("매물 메모") }, minLines = 10, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${draft.length} / 2000") })
            ActionNotice(action)
            Button(enabled = !action.busy && draft != document.freeMemo, modifier = Modifier.fillMaxWidth(), onClick = { action.run("메모를 저장했어요.") { api.saveMemo(propertyId, draft); memo.reload() } }) { Text("메모 저장") }
        }
    }
}
