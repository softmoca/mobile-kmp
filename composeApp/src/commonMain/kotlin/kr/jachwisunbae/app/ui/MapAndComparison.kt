package kr.jachwisunbae.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kr.jachwisunbae.app.data.*
import kr.jachwisunbae.app.platform.*

internal data class NearbyQuery(val center: Coordinates, val radius: Int, val categories: Set<MapCategory>)

/** Map selections remain interactive; only results for the currently selected query may be shown. */
internal suspend fun <T> fetchCurrentNearby(request: NearbyQuery, current: () -> NearbyQuery?, fetch: suspend () -> T): T? {
    return try {
        val result = fetch()
        result.takeIf { current() == request }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        if (current() == request) throw failure else null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun MapScreen(api: ApiClient, platform: PlatformServices, revision: Int, onOpen: (Long) -> Unit) {
    val properties = rememberRefreshLoad("map-properties", revision) { api.properties() }
    var selected by remember { mutableStateOf<Coordinates?>(null) }
    var nearby by remember { mutableStateOf<NearbyDto?>(null) }
    var radius by rememberSaveable { mutableStateOf(500) }
    var categories by remember { mutableStateOf(setOf<MapCategory>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var addresses by remember { mutableStateOf<List<AddressDto>?>(null) }
    val action = rememberAction()
    Page("지도", actions = { TextButton(onClick = properties::reload) { Text("새로고침") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { OutlinedTextField(query, { query = it; addresses = null; action.clear() }, label = { Text("주소 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
                TextButton(enabled = !action.busy && query.isNotBlank(), onClick = { val submitted = query.trim(); action.run { val found = api.geocode(submitted); if (query.trim() == submitted) addresses = found } }) { Text("검색") }
            }) }
            item { ActionNotice(action) }
            addresses?.let { found ->
                if (found.isEmpty()) item { Notice("검색 결과가 없어요. 주소를 자세히 입력해 주세요.") }
                items(found) { address -> OutlinedCard(onClick = { selected = Coordinates(address.latitude, address.longitude); nearby = null; addresses = null }, modifier = Modifier.fillMaxWidth()) {
                    Text(address.roadAddress ?: address.jibunAddress ?: "검색한 위치", Modifier.padding(14.dp))
                } }
            }
            item {
                val markers = properties.value?.items.orEmpty().mapNotNull { property ->
                    property.latitude?.let { latitude -> property.longitude?.let { longitude -> MapMarker("property:${property.id}", property.name, latitude, longitude) } }
                } + nearby?.places.orEmpty().map { MapMarker("place:${it.category}:${it.providerPlaceId}", it.name, it.latitude, it.longitude) }
                NativeMap(markers, selected, onLocationSelected = { selected = it; nearby = null; action.clear() }, modifier = Modifier.fillMaxWidth().height(330.dp))
            }
            item { OutlinedButton(enabled = !action.busy, onClick = { action.run { selected = platform.currentLocation(); nearby = null } }, modifier = Modifier.fillMaxWidth()) { Text("현재 위치로 이동") } }
            item { SectionTitle("주변 생활 편의시설", "지도 또는 등록 매물에서 기준 위치를 선택해 주세요.") }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(500, 1000, 2000).forEach { value -> FilterChip(selected = radius == value, onClick = { radius = value; nearby = null; action.clear() }, label = { Text(if (value < 1000) "500m" else "${value / 1000}km") }) } } }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapCategory.entries.forEach { category -> FilterChip(selected = category in categories, onClick = { categories = if (category in categories) emptySet() else setOf(category); nearby = null; action.clear() }, label = { Text(category.label()) }) }
            } }
            item { Button(enabled = !action.busy && selected != null, modifier = Modifier.fillMaxWidth(), onClick = {
                selected?.let { center ->
                    val request = NearbyQuery(center, radius, categories)
                    action.run {
                        val result = fetchCurrentNearby(request, current = { selected?.let { NearbyQuery(it, radius, categories) } }) {
                            api.nearby(request.center.latitude, request.center.longitude, request.radius, request.categories.toList())
                        }
                        if (result != null) nearby = result
                    }
                }
            }) { Text(if (categories.isEmpty()) "주변 전체 시설 분석" else "선택한 시설 분석") } }
            nearby?.let { result ->
                item { Notice("반경 ${result.radius}m · ${result.counts.entries.joinToString(" · ") { "${it.key.label()} ${it.value}개" }}") }
                if (result.places.isEmpty()) item { EmptyState("이 범위에 시설이 없어요", "반경이나 카테고리를 바꿔보세요.") }
                items(result.places, key = { "${it.category}:${it.providerPlaceId}" }) { place -> OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(place.name, fontWeight = FontWeight.Bold)
                        Text("${place.category.label()} · ${place.distanceMeters}m", style = MaterialTheme.typography.bodySmall)
                        if (place.address.isNotBlank()) Text(place.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
            }
            item { HorizontalDivider(); Spacer(Modifier.height(14.dp)); SectionTitle("등록한 매물") }
            item { if (properties.error != null) { Notice(properties.error!!, true); TextButton(onClick = properties::reload) { Text("다시 불러오기") } } else if (properties.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (properties.value?.items?.isEmpty() == true) item { Notice("등록된 매물이 없어요. 내 매물에서 첫 번째 집을 등록해 보세요.") }
            items(properties.value?.items.orEmpty(), key = { it.id }) { property -> OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(property.name, fontWeight = FontWeight.Bold)
                    Text(property.address ?: "주소 미입력", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = property.latitude != null && property.longitude != null, onClick = { selected = Coordinates(property.latitude!!, property.longitude!!); nearby = null }) { Text("위치 선택") }
                        TextButton(onClick = { onOpen(property.id) }) { Text("매물 보기") }
                    }
                }
            } }
        }
    }
}

private data class ComparisonEntry(val property: PropertyDetailDto, val memo: String, val checklists: List<AppliedChecklistDto>)

@Composable internal fun ComparisonScreen(api: ApiClient, platform: PlatformServices, ids: List<Long>, revision: Int, onBack: () -> Unit) {
    val comparison = rememberLoad(ids, revision) {
        coroutineScope { ids.map { id -> async {
            val property = api.property(id)
            val overview = api.checklistOverview(id)
            ComparisonEntry(property, api.memo(id).freeMemo, overview.stages.mapNotNull { it.propertyChecklistId }.map { api.appliedChecklist(id, it) })
        } }.awaitAll() }
    }
    val action = rememberAction()
    var pdf by remember(ids, revision) { mutableStateOf<ByteArray?>(null) }
    Page("매물 비교", onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ids.size !in 2..5) { Notice("비교할 매물을 2~5개 선택해 주세요.", true); return@Column }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${ids.size}개의 집, 내 기준으로 비교해 보세요.", style = MaterialTheme.typography.bodyMedium)
                ActionNotice(action)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("미리보기", "저장", "공유").forEachIndexed { index, label ->
                        OutlinedButton(enabled = !action.busy, modifier = Modifier.weight(1f), onClick = { action.run {
                            val bytes = pdf ?: api.comparisonPdf(ids).also { pdf = it }
                            val filename = "jachwi-comparison.pdf"
                            when (index) { 0 -> platform.previewPdf(bytes, filename); 1 -> platform.savePdf(bytes, filename); else -> platform.sharePdf(bytes, filename) }
                        } }) { Text(label, maxLines = 1) }
                    }
                }
                Text("PDF는 서버의 최신 매물 정보로 생성됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Resource(comparison) { entries ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        entries.forEach { entry ->
                            OutlinedCard(Modifier.width(280.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                val property = entry.property
                                Text(property.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(property.address ?: "주소 미입력", style = MaterialTheme.typography.bodySmall)
                                property.representativePhoto?.let { PhotoImage(api, property.id, it.id, property.name, Modifier.fillMaxWidth().height(150.dp)) }
                                    ?: PhotoPlaceholder(Modifier.fillMaxWidth().height(150.dp))
                                InfoRow("보증금", money(property.depositAmount)); InfoRow("월세", money(property.monthlyRentAmount)); InfoRow("관리비", money(property.maintenanceFeeAmount))
                                InfoRow("입주 가능", property.availableMoveInDate ?: "미입력")
                                InfoRow("방 옵션", property.roomOptions.mapNotNull { RoomOptions[it] }.joinToString(", ").ifBlank { "미입력" })
                                InfoRow("포함 공과금", property.utilityOptions.mapNotNull { UtilityOptions[it] }.joinToString(", ").ifBlank { "미입력" })
                                Progress(property.overallProgress)
                                HorizontalDivider()
                                SectionTitle("자유 메모"); Text(entry.memo.ifBlank { "작성한 메모가 없어요." }, style = MaterialTheme.typography.bodyMedium)
                                entry.checklists.forEach { checklist ->
                                    HorizontalDivider(); SectionTitle(checklist.stage.label(), checklist.checklistName)
                                    checklist.items.forEach { item ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("${item.status.label()} · ${item.question}", style = MaterialTheme.typography.bodyMedium, color = item.status.ink())
                                            if (item.memo.isNotBlank()) Text(item.memo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                if (entry.checklists.isEmpty()) Text("적용한 체크리스트가 없어요.", style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                    }
                }
            }
        }
    }
}
