package kr.jachwisunbae.app.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val mutableSession = MutableStateFlow<AuthSession>(AuthSession.Loading)
    val session: StateFlow<AuthSession> = mutableSession.asStateFlow()
    private val token = MutableStateFlow<String?>(null)
    private val sessionMutex = Mutex()
    private var sessionRevision = 0L

    init {
        val url = Url(this.baseUrl)
        require(url.protocol.name == "https" && url.host.isNotBlank() && url.user.isNullOrEmpty() && url.password.isNullOrEmpty()) {
            "API에는 인증 정보가 없는 HTTPS 주소를 사용해야 합니다."
        }
        require(url.encodedPath in listOf("", "/") && url.parameters.isEmpty() && url.fragment.isEmpty())
    }

    suspend fun restoreSession() {
        var revision = -1L
        try {
            val saved = sessionMutex.withLock {
                revision = ++sessionRevision
                mutableSession.value = AuthSession.Loading
                token.value = null
                storage { tokenStore.read() }?.takeIf { it.isNotBlank() }.also { token.value = it }
            }
            if (saved == null) {
                sessionMutex.withLock {
                    if (revision == sessionRevision) mutableSession.value = AuthSession.LoggedOut()
                }
                return
            }
            val member = member()
            sessionMutex.withLock {
                if (revision == sessionRevision && token.value == saved) mutableSession.value = AuthSession.Authenticated(member)
            }
        } catch (error: ApiException) {
            // 일시적인 연결 실패는 저장된 토큰을 지우지 않아 다시 확인할 수 있다.
            sessionMutex.withLock {
                if (revision == sessionRevision) mutableSession.value = AuthSession.LoggedOut(error.message)
            }
        }
    }

    suspend fun login(nickname: String, password: String? = null): LoginDto {
        InputValidation.requireValid(InputValidation.login(nickname, password))
        val result = jsonRequest<LoginDto>("/api/auth/nickname", HttpMethod.Post, authenticated = false) {
            jsonBody(LoginRequest(nickname.trim(), password?.takeIf { it.isNotEmpty() }))
        }
        if (!result.tokenType.equals("Bearer", ignoreCase = true) || result.accessToken.isBlank() || result.expiresIn <= 0) {
            throw invalidResponse()
        }
        sessionMutex.withLock {
            storage { tokenStore.write(result.accessToken) }
            sessionRevision++
            token.value = result.accessToken
            mutableSession.value = AuthSession.Authenticated(MemberDto(result.member.memberId, result.member.name, result.member.passwordProtected))
        }
        return result
    }

    suspend fun logout() = sessionMutex.withLock {
        // 화면 전환이 호출 코루틴을 취소해도 보안 저장소 제거를 끝낸 뒤 로그인 화면으로 전환한다.
        withContext(NonCancellable) {
            sessionRevision++
            token.value = null
            try {
                storage { tokenStore.clear() }
                mutableSession.value = AuthSession.LoggedOut()
            } catch (error: ApiException) {
                mutableSession.value = AuthSession.LoggedOut(error.message)
                throw error
            }
        }
    }

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
        return jsonRequest("/api/maps/nearby") {
            parameter("latitude", latitude); parameter("longitude", longitude); parameter("radius", radius)
            if (categories.isNotEmpty()) parameter("categories", categories.distinct().joinToString(",") { it.name })
        }
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
    ): HttpResponse {
        val sentToken = if (authenticated) token.value ?: throw ApiException(401, "AUTH_REQUIRED", "로그인이 필요합니다.") else null
        val response = try {
            client.request(baseUrl + path) {
                this.method = method
                expectSuccess = false
                sentToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                configure()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw ApiException(0, "NETWORK_ERROR", "서버에 연결하지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요.", cause = error)
        }
        if (response.status.value in 200..299) return response
        if (response.status.value == 401 && sentToken != null) expireSession(sentToken)
        val error = try { json.decodeFromString<ErrorEnvelope>(response.bodyAsText()) } catch (_: SerializationException) { null }
        throw ApiException(
            response.status.value,
            error?.code ?: "HTTP_${response.status.value}",
            error?.message?.takeIf { it.isNotBlank() } ?: when (response.status.value) {
                401 -> "로그인이 만료되었습니다. 다시 로그인해 주세요."
                403 -> "이 정보에 접근할 권한이 없습니다."
                404 -> "요청한 정보를 찾을 수 없습니다."
                else -> "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."
            },
            error?.errors ?: emptyList(),
        )
    }

    private suspend fun expireSession(sentToken: String) = sessionMutex.withLock {
        // 이전 요청의 401이 새 로그인 세션까지 지우지 않도록 요청 당시 토큰을 비교한다.
        if (token.value == sentToken) {
            withContext(NonCancellable) {
                sessionRevision++
                token.value = null
                try {
                    storage { tokenStore.clear() }
                    mutableSession.value = AuthSession.LoggedOut("로그인이 만료되었습니다. 다시 로그인해 주세요.")
                } catch (error: ApiException) {
                    mutableSession.value = AuthSession.LoggedOut(error.message)
                    throw error
                }
            }
        }
    }

    private suspend fun <T> storage(operation: suspend () -> T): T = try {
        operation()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        throw ApiException(0, "SECURE_STORAGE_ERROR", "보안 저장소를 사용할 수 없습니다. 다시 시도해 주세요.", cause = error)
    }

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
