package kr.jachwisunbae.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kr.jachwisunbae.app.data.*
import kr.jachwisunbae.app.platform.*

@Composable internal fun PropertyEditorScreen(api: ApiClient, id: Long?, platform: PlatformServices, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val initial = rememberLoad(id) { id?.let { api.property(it) } }
    Page(if (id == null) "매물 등록" else "매물 수정", onBack) { padding ->
        if (id == null) PropertyForm(api, null, platform, padding, onSaved)
        else Resource(initial) { PropertyForm(api, it, platform, padding, onSaved) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PropertyForm(api: ApiClient, initial: PropertyDetailDto?, platform: PlatformServices, padding: PaddingValues, onSaved: (Long) -> Unit) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "새 매물") }
    var deposit by rememberSaveable(initial?.id) { mutableStateOf(initial?.depositAmount?.toString() ?: "0") }
    var rent by rememberSaveable(initial?.id) { mutableStateOf(initial?.monthlyRentAmount?.toString() ?: "0") }
    var maintenance by rememberSaveable(initial?.id) { mutableStateOf(initial?.maintenanceFeeAmount?.toString().orEmpty()) }
    var address by rememberSaveable(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }
    var latitude by rememberSaveable(initial?.id) { mutableStateOf(initial?.latitude) }
    var longitude by rememberSaveable(initial?.id) { mutableStateOf(initial?.longitude) }
    var source by rememberSaveable(initial?.id) { mutableStateOf(initial?.discoverySource.orEmpty()) }
    var moveIn by rememberSaveable(initial?.id) { mutableStateOf(initial?.availableMoveInDate.orEmpty()) }
    var visit by rememberSaveable(initial?.id) { mutableStateOf(initial?.visitScheduledAt.orEmpty()) }
    var roomOptions by remember(initial?.id) { mutableStateOf(initial?.roomOptions?.toSet() ?: emptySet()) }
    var utilities by remember(initial?.id) { mutableStateOf(initial?.utilityOptions?.toSet() ?: emptySet()) }
    var validation by remember { mutableStateOf<String?>(null) }
    val action = rememberAction()
    ScrollBody(padding) {
        SectionTitle("기본 정보", "금액은 원 단위로 입력해 주세요. 미확정 금액은 0으로 기록할 수 있어요.")
        OutlinedTextField(name, { if (it.length <= 30) name = it }, label = { Text("매물 이름 *") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("property-name-input"), supportingText = { Text("${name.length} / 30") })
        AmountField("보증금 (원) *", deposit) { deposit = it }
        AmountField("월세 (원) *", rent) { rent = it }
        AmountField("관리비 (원)", maintenance) { maintenance = it }
        SectionTitle("관리비 포함 공과금")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UtilityOptions.forEach { (key, label) -> FilterChip(selected = key in utilities, onClick = { utilities = if (key in utilities) utilities - key else utilities + key }, label = { Text(label) }) }
        }
        HorizontalDivider()
        SectionTitle("매물 위치", "주소 검색 또는 지도에서 실제 위치를 선택해 주세요.")
        AddressPicker(api, platform, latitude?.let { lat -> longitude?.let { lon -> Coordinates(lat, lon) } }) { found ->
            address = found.roadAddress ?: found.jibunAddress.orEmpty(); latitude = found.latitude; longitude = found.longitude
        }
        OutlinedTextField(address, { address = it.take(255); latitude = null; longitude = null }, label = { Text("주소") }, modifier = Modifier.fillMaxWidth().testTag("property-address-input"), supportingText = { Text(if (latitude == null) "직접 입력한 주소는 좌표가 없어 지도에 표시되지 않아요." else "선택한 주소의 좌표가 함께 저장됩니다.") })
        if (latitude != null) Text("위도 $latitude · 경도 $longitude", style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        SectionTitle("추가 정보")
        OutlinedTextField(source, { source = it.take(500) }, label = { Text("발견 경로 · 링크 · 중개사 연락처") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(moveIn, { moveIn = it }, label = { Text("입주 가능일") }, placeholder = { Text("2026-10-31") }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("YYYY-MM-DD, 미정이면 비워두세요.") })
        OutlinedTextField(visit, { visit = it }, label = { Text("방문 예정 시각") }, placeholder = { Text("2026-10-20T14:30") }, singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("YYYY-MM-DDTHH:mm, 한국 현지 시각") })
        SectionTitle("방 옵션")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoomOptions.forEach { (key, label) -> FilterChip(selected = key in roomOptions, onClick = { roomOptions = if (key in roomOptions) roomOptions - key else roomOptions + key }, label = { Text(label) }) }
        }
        validation?.let { Notice(it, true) }
        ActionNotice(action)
        Button(enabled = !action.busy, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("property-submit"), onClick = {
            val depositAmount = deposit.toLongOrNull(); val monthlyAmount = rent.toLongOrNull(); val fee = maintenance.takeIf { it.isNotBlank() }?.toLongOrNull()
            when {
                name.isBlank() -> validation = "매물 이름을 입력해 주세요."
                depositAmount == null || monthlyAmount == null || (maintenance.isNotBlank() && fee == null) -> validation = "금액은 0 이상의 정수로 입력해 주세요."
                else -> {
                    validation = null
                    val request = PropertyInput(name = name.trim(), depositAmount = depositAmount, monthlyRentAmount = monthlyAmount,
                        address = address.takeIf { it.isNotBlank() }, latitude = latitude, longitude = longitude,
                        availableMoveInDate = moveIn.takeIf { it.isNotBlank() }, maintenanceFeeAmount = fee,
                        visitScheduledAt = visit.takeIf { it.isNotBlank() }, discoverySource = source.takeIf { it.isNotBlank() },
                        roomOptions = roomOptions.toList(), utilityOptions = utilities.toList())
                    action.run {
                        if (initial == null) onSaved(api.createProperty(request).id)
                        else { api.updateProperty(initial.id, request); onSaved(initial.id) }
                    }
                }
            }
        }) { Text(if (action.busy) "저장 중…" else if (initial == null) "매물 등록" else "변경 사항 저장") }
    }
}

@Composable private fun AmountField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { if (it.all(Char::isDigit)) onChange(it) }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

@Composable internal fun AddressPicker(api: ApiClient, platform: PlatformServices, selected: Coordinates?, onSelected: (AddressDto) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AddressDto>?>(null) }
    val action = rememberAction()
    var mapVisible by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, { query = it; results = null; action.clear() }, label = { Text("주소 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
            TextButton(enabled = !action.busy && query.isNotBlank(), onClick = { val submitted = query.trim(); action.run { val found = api.geocode(submitted); if (query.trim() == submitted) results = found } }) { Text("검색") }
        })
        ActionNotice(action)
        results?.let { addresses ->
            if (addresses.isEmpty()) Notice("찾은 주소가 없어요. 도로명이나 건물명을 자세히 입력해 주세요.")
            addresses.forEach { found ->
                OutlinedCard(onClick = { onSelected(found); results = null; mapVisible = true }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(found.roadAddress ?: found.jibunAddress ?: "선택한 위치")
                        found.jibunAddress?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { mapVisible = !mapVisible }) { Text(if (mapVisible) "지도 접기" else "지도에서 선택") }
            OutlinedButton(enabled = !action.busy, onClick = { action.run {
                val location = platform.currentLocation()
                onSelected(api.reverseGeocode(location.latitude, location.longitude)); mapVisible = true
            } }) { Text("현재 위치") }
        }
        if (mapVisible) NativeMap(markers = emptyList(), selectedLocation = selected, onLocationSelected = { location ->
            action.run { onSelected(api.reverseGeocode(location.latitude, location.longitude)) }
        }, modifier = Modifier.fillMaxWidth().height(250.dp))
    }
}
