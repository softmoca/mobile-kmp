package kr.jachwisunbae.app.data

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kr.jachwisunbae.app.domain.InputValidation

/** Controller 계약을 직접 표현하는 작은 저장소. JSON과 바이너리 응답 모두 같은 인증 경계를 쓴다. */
class ApiClient(
    private val client: HttpClient,
    private val tokenStore: TokenStore,
    baseUrl: String,
) {
    private val baseUrl = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val sessions = SessionManager(client, tokenStore, this.baseUrl, json)
    val session: StateFlow<AuthSession> = sessions.session

    init {
        val url = Url(this.baseUrl)
        require(url.protocol.name == "https" && url.host.isNotBlank() && url.user.isNullOrEmpty() && url.password.isNullOrEmpty()) {
            "API에는 인증 정보가 없는 HTTPS 주소를 사용해야 합니다."
        }
        require(url.encodedPath in listOf("", "/") && url.parameters.isEmpty() && url.fragment.isEmpty())
    }

    suspend fun bootstrapSession() = sessions.bootstrapSession()
    suspend fun restoreSession() = sessions.restoreSession()
    suspend fun login(): LoginDto = sessions.login()
    suspend fun startNewSession(): LoginDto = sessions.startNewSession()
    suspend fun refresh(): LoginDto = sessions.refresh()
    suspend fun logout() = sessions.logout()

    suspend fun member(): MemberDto = jsonRequest("/api/members/me")
    suspend fun properties(): PropertyListDto = jsonRequest("/api/properties")
    suspend fun property(id: Long): PropertyDetailDto = jsonRequest("/api/properties/${id.pathId()}")

    suspend fun createProperty(input: PropertyInput): PropertyDetailDto {
        InputValidation.requireValid(InputValidation.property(input))
        return jsonRequest("/api/properties", HttpMethod.Post) { jsonBody(input) }
    }

    suspend fun updateProperty(id: Long, input: PropertyInput): PropertyUpdateDto {
        InputValidation.requireValid(InputValidation.property(input))
        return jsonRequest("/api/properties/${id.pathId()}", HttpMethod.Put) { jsonBody(input) }
    }

    suspend fun deleteProperty(id: Long) = noContent("/api/properties/${id.pathId()}", HttpMethod.Delete)
    suspend fun memo(id: Long): PropertyMemoDto = jsonRequest("/api/properties/${id.pathId()}/memo")

    suspend fun saveMemo(id: Long, freeMemo: String): PropertyMemoDto {
        validateLength("freeMemo", freeMemo, 2000)
        return jsonRequest("/api/properties/${id.pathId()}/memo", HttpMethod.Put) { jsonBody(MemoRequest(freeMemo)) }
    }

    suspend fun photos(id: Long): PhotoListDto = jsonRequest("/api/properties/${id.pathId()}/photos")

    suspend fun uploadPhoto(id: Long, bytes: ByteArray, fileName: String, contentType: String): PhotoDto {
        if (bytes.isEmpty() || bytes.size > 5 * 1024 * 1024) {
            throw ApiException(400, "CLIENT_VALIDATION", "사진은 1바이트 이상 5MiB 이하여야 합니다.")
        }
        if (contentType.lowercase() !in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) {
            throw ApiException(400, "CLIENT_VALIDATION", "JPEG, PNG, WebP, HEIC, HEIF 사진을 선택해 주세요.")
        }
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
            .filter { it.isLetterOrDigit() || it in ".-_" }.take(100).ifBlank { "photo" }
        return jsonRequest("/api/properties/${id.pathId()}/photos", HttpMethod.Post) {
            setBody(MultiPartFormDataContent(formData {
                append("file", bytes, Headers.build {
                    append(HttpHeaders.ContentType, contentType.lowercase())
                    append(HttpHeaders.ContentDisposition, "filename=\"$safeName\"")
                })
            }))
        }
    }

    suspend fun photoBytes(propertyId: Long, photoId: Long): ByteArray =
        binaryRequest("/api/properties/${propertyId.pathId()}/photos/${photoId.pathId()}", expectedContentType = "image/")

    suspend fun deletePhoto(propertyId: Long, photoId: Long) =
        noContent("/api/properties/${propertyId.pathId()}/photos/${photoId.pathId()}", HttpMethod.Delete)

    suspend fun setRepresentativePhoto(propertyId: Long, photoId: Long) =
        noContent("/api/properties/${propertyId.pathId()}/photos/${photoId.pathId()}/representative", HttpMethod.Put)

    suspend fun systemCheckItems(stage: CheckStage, query: String? = null): List<SystemCheckItemDto> {
        if (query != null) validateLength("query", query, 200)
        return jsonRequest("/api/check-items") {
            parameter("stage", stage.name)
            query?.trim()?.takeIf { it.isNotEmpty() }?.let { parameter("query", it) }
        }
    }

    suspend fun userChecklists(stage: CheckStage? = null): UserChecklistListDto = jsonRequest("/api/checklists") {
        stage?.let { parameter("stage", it.name) }
    }

    suspend fun userChecklist(id: Long): UserChecklistDto = jsonRequest("/api/checklists/${id.pathId()}")

    suspend fun createUserChecklist(name: String, stage: CheckStage, systemItemIds: List<Long>): UserChecklistDto {
        InputValidation.requireValid(InputValidation.checklist(name, systemItemIds))
        return jsonRequest("/api/checklists", HttpMethod.Post) {
            jsonBody(CreateUserChecklistRequest(name.trim(), stage, systemItemIds.map(::UserChecklistItemInput)))
        }
    }

    suspend fun updateUserChecklist(id: Long, name: String, systemItemIds: List<Long>): UserChecklistDto {
        InputValidation.requireValid(InputValidation.checklist(name, systemItemIds))
        return jsonRequest("/api/checklists/${id.pathId()}", HttpMethod.Put) {
            jsonBody(UpdateUserChecklistRequest(name.trim(), systemItemIds.map(::UserChecklistItemInput)))
        }
    }

    suspend fun deleteUserChecklist(id: Long) = noContent("/api/checklists/${id.pathId()}", HttpMethod.Delete)
    suspend fun checklistOverview(propertyId: Long): ChecklistOverviewDto = jsonRequest("/api/properties/${propertyId.pathId()}/checklists")
    suspend fun appliedChecklist(propertyId: Long, checklistId: Long): AppliedChecklistDto =
        jsonRequest("/api/properties/${propertyId.pathId()}/checklists/${checklistId.pathId()}")

    suspend fun applyChecklist(propertyId: Long, stage: CheckStage, source: ChecklistSource, checklistId: Long? = null): AppliedChecklistDto {
        if ((source == ChecklistSource.USER && (checklistId == null || checklistId <= 0)) ||
            (source == ChecklistSource.SYSTEM_DEFAULT && checklistId != null)) {
            throw ApiException(400, "CLIENT_VALIDATION", "적용할 체크리스트를 확인해 주세요.")
        }
        return jsonRequest("/api/properties/${propertyId.pathId()}/checklists/${stage.name}", HttpMethod.Put) {
            jsonBody(ApplyChecklistRequest(source, checklistId))
        }
    }

    suspend fun updateCheckStatus(propertyId: Long, checklistId: Long, itemId: Long, status: CheckStatus): CheckStatusDto =
        jsonRequest(itemPath(propertyId, checklistId, itemId) + "/status", HttpMethod.Patch) { jsonBody(CheckStatusRequest(status)) }

    suspend fun updateCheckMemo(propertyId: Long, checklistId: Long, itemId: Long, memo: String): CheckMemoDto {
        validateLength("memo", memo, 500)
        return jsonRequest(itemPath(propertyId, checklistId, itemId) + "/memo", HttpMethod.Patch) { jsonBody(CheckMemoRequest(memo)) }
    }

    suspend fun geocode(query: String): List<AddressDto> {
        if (query.trim().isEmpty()) throw ApiException(400, "CLIENT_VALIDATION", "검색할 주소를 입력해 주세요.")
        validateLength("query", query, 200)
        return jsonRequest("/api/maps/geocode") { parameter("query", query.trim()) }
    }

    suspend fun reverseGeocode(latitude: Double, longitude: Double): AddressDto {
        validateCoordinates(latitude, longitude)
        return jsonRequest("/api/maps/reverse-geocode") { parameter("latitude", latitude); parameter("longitude", longitude) }
    }

    suspend fun nearby(latitude: Double, longitude: Double, radius: Int = 500, categories: List<MapCategory> = emptyList()): NearbyDto {
        validateCoordinates(latitude, longitude)
        if (radius !in setOf(500, 1000, 2000)) throw ApiException(400, "CLIENT_VALIDATION", "반경은 500m, 1km, 2km 중 선택해 주세요.")
        val selected = categories.ifEmpty { MapCategory.entries.toList() }.distinct()
        val ordinary = selected.filter { it != MapCategory.TRANSPORT }
        val nearby: NearbyDto = if (ordinary.isEmpty()) {
            NearbyDto(CoordinateDto(latitude, longitude), radius, emptyMap(), emptyList())
        } else {
            jsonRequest("/api/maps/nearby") {
                parameter("latitude", latitude); parameter("longitude", longitude); parameter("radius", radius)
                parameter("categories", ordinary.joinToString(",") { it.name })
            }
        }
        if (MapCategory.TRANSPORT !in selected) return nearby
        val transport = busStops(latitude, longitude, radius)
        return nearby.copy(
            counts = nearby.counts + (MapCategory.TRANSPORT to transport.count),
            places = nearby.places + transport.busStops.map { stop ->
                NearbyPlaceDto(stop.nodeId, stop.name, MapCategory.TRANSPORT, "", stop.latitude, stop.longitude, stop.distanceMeters)
            },
        )
    }

    suspend fun busStops(latitude: Double, longitude: Double, radius: Int = 500): BusStopsDto {
        validateCoordinates(latitude, longitude)
        if (radius !in setOf(500, 1000, 2000)) throw ApiException(400, "CLIENT_VALIDATION", "반경은 500m, 1km, 2km 중 선택해 주세요.")
        val result: BusStopsDto = jsonRequest("/api/maps/bus-stops") {
            parameter("latitude", latitude); parameter("longitude", longitude); parameter("radius", radius)
        }
        if (result.radius != radius || result.count != result.busStops.size ||
            result.busStops.map { it.nodeId }.distinct().size != result.count ||
            result.busStops.any { it.nodeId.isBlank() || it.name.isBlank() || it.distanceMeters < 0 ||
                !it.latitude.isFinite() || it.latitude !in -90.0..90.0 ||
                !it.longitude.isFinite() || it.longitude !in -180.0..180.0 }) throw invalidResponse()
        return result
    }

    suspend fun comparisonPdf(propertyIds: List<Long>): ByteArray {
        InputValidation.requireValid(InputValidation.comparison(propertyIds))
        val bytes = binaryRequest("/api/properties/export.pdf", HttpMethod.Post, "application/pdf") { jsonBody(ComparisonRequest(propertyIds)) }
        if (bytes.size < 5 || bytes.copyOfRange(0, 5).decodeToString() != "%PDF-") throw invalidResponse()
        return bytes
    }

    private suspend inline fun <reified T> jsonRequest(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        authenticated: Boolean = true,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response = send(path, method, authenticated, configure)
        try {
            val envelope = json.decodeFromString<ApiEnvelope<T>>(response.bodyAsText())
            if (envelope.code != "SUCCESS") throw invalidResponse()
            return envelope.data
        } catch (error: SerializationException) {
            throw invalidResponse(error)
        } catch (error: IllegalArgumentException) {
            throw invalidResponse(error)
        }
    }

    private suspend fun noContent(path: String, method: HttpMethod) {
        val response = send(path, method)
        if (response.status.value != 204) throw invalidResponse()
    }

    private suspend fun binaryRequest(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        expectedContentType: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): ByteArray {
        val response = send(path, method, configure = configure)
        val type = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.lowercase().orEmpty()
        val validType = if (expectedContentType.endsWith('/')) type.startsWith(expectedContentType) else type == expectedContentType
        if (!validType) throw invalidResponse()
        return response.bodyAsBytes().also { if (it.isEmpty()) throw invalidResponse() }
    }

    private suspend fun send(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        authenticated: Boolean = true,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = sessions.request(path, method, authenticated, configure)

    private inline fun <reified T> HttpRequestBuilder.jsonBody(value: T) {
        setBody(TextContent(json.encodeToString(value), ContentType.Application.Json))
    }

    private fun Long.pathId(): String {
        if (this <= 0) throw ApiException(400, "CLIENT_VALIDATION", "올바른 항목을 선택해 주세요.")
        return toString()
    }

    private fun itemPath(propertyId: Long, checklistId: Long, itemId: Long) =
        "/api/properties/${propertyId.pathId()}/checklists/${checklistId.pathId()}/items/${itemId.pathId()}"

    private fun validateLength(field: String, value: String, max: Int) {
        if (value.length > max) InputValidation.requireValid(listOf(FieldErrorDto(field, "최대 ${max}자까지 입력할 수 있습니다.")))
    }

    private fun validateCoordinates(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            throw ApiException(400, "CLIENT_VALIDATION", "올바른 위치를 선택해 주세요.")
        }
    }

    private fun invalidResponse(cause: Throwable? = null) =
        ApiException(0, "INVALID_RESPONSE", "서버 응답을 읽을 수 없습니다. 잠시 후 다시 시도해 주세요.", cause = cause)
}
