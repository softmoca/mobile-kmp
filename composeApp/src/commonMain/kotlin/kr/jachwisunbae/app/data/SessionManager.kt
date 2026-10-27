package kr.jachwisunbae.app.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Refresh 쿠키는 API origin의 /api/auth 요청에만 직접 전달한다. 플랫폼 쿠키 저장소를 사용하지 않는다. */
internal class SessionManager(
    private val client: HttpClient,
    private val store: TokenStore,
    private val origin: String,
    private val json: Json,
) {
    private data class Credentials(
        val identity: Long = 0,
        val stored: StoredSession? = null,
        val accessToken: String? = null,
        val refreshCycle: Long = 0,
        val refreshFailure: ApiException? = null,
    )

    private val credentials = MutableStateFlow(Credentials())
    private val mutableSession = MutableStateFlow<AuthSession>(AuthSession.Loading)
    val session: StateFlow<AuthSession> = mutableSession.asStateFlow()
    private val mutex = Mutex()
    private var pendingLogin: Pair<LoginDto, String>? = null

    /** 최초 설치만 자동 시작한다. 복원 실패·구버전·명시 로그아웃은 사용자 확인 전 새 회원을 만들지 않는다. */
    suspend fun bootstrapSession() {
        restoreSession()
        if (session.value == AuthSession.LoggedOut()) {
            try { login() } catch (_: ApiException) { /* 실패 상태는 login에서 반영한다. */ }
        }
    }

    suspend fun restoreSession() = mutex.withLock {
        withContext(NonCancellable) {
            mutableSession.value = AuthSession.Loading
            try {
                pendingLogin?.let { (result, cookie) ->
                    persistLogin(result, cookie)
                    return@withContext
                }
                val saved = readStored()
                if (saved == null) {
                    credentials.value = Credentials(identity = credentials.value.identity + 1)
                    mutableSession.value = AuthSession.LoggedOut()
                } else {
                    requireRestorable(saved)
                    credentials.value = Credentials(identity = credentials.value.identity + 1, stored = saved)
                    renewLocked(credentials.value)
                }
            } catch (error: ApiException) {
                showRecoveryFailure(error)
            }
        }
    }

    suspend fun login(): LoginDto = mutex.withLock {
        withContext(NonCancellable) {
            mutableSession.value = AuthSession.Loading
            try {
                pendingLogin?.let { (result, cookie) ->
                    persistLogin(result, cookie)
                    return@withContext result
                }
                val saved = readStored()
                if (saved == null) createNewLocked()
                else {
                    requireRestorable(saved)
                    credentials.value = Credentials(identity = credentials.value.identity + 1, stored = saved)
                    renewLocked(credentials.value)
                }
            } catch (error: ApiException) {
                showRecoveryFailure(error)
                throw error
            }
        }
    }

    /** 이전 기록에 접근할 수 없게 될 수 있음을 사용자가 확인한 뒤에만 호출한다. */
    suspend fun startNewSession(): LoginDto = mutex.withLock {
        withContext(NonCancellable) {
            mutableSession.value = AuthSession.Loading
            try {
                pendingLogin?.let { (result, cookie) ->
                    persistLogin(result, cookie)
                    return@withContext result
                }
                createNewLocked()
            }
            catch (error: ApiException) { showRecoveryFailure(error); throw error }
        }
    }

    suspend fun refresh(): LoginDto = mutex.withLock {
        withContext(NonCancellable) {
            pendingLogin?.let { (result, cookie) ->
                persistLogin(result, cookie)
                return@withContext result
            }
            val current = credentials.value
            val saved = current.stored ?: readStored() ?: throw required()
            requireRestorable(saved)
            val active = if (current.stored == saved) current else Credentials(current.identity + 1, saved)
            credentials.value = active
            renewLocked(active)
        }
    }

    suspend fun logout() = mutex.withLock {
        withContext(NonCancellable) { invalidateLocked(LOST_SESSION) }
    }

    suspend fun request(
        path: String,
        method: HttpMethod,
        authenticated: Boolean = true,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        if (!authenticated) {
            val response = transport(path, method, configure = configure)
            if (response.status.value !in 200..299) throw responseError(response)
            return response
        }
        val sent = credentials.value
        val access = sent.accessToken ?: throw required()
        val first = transport(path, method, access = access, configure = configure)
        if (credentials.value.identity != sent.identity) throw changed()
        if (first.status.value != 401) {
            if (first.status.value !in 200..299) throw responseError(first)
            return first
        }
        val refreshed = mutex.withLock {
            withContext(NonCancellable) {
                val current = credentials.value
                if (current.identity != sent.identity) throw changed()
                if (current.refreshCycle != sent.refreshCycle) {
                    current.refreshFailure?.let { throw it }
                } else {
                    renewLocked(current)
                }
                credentials.value
            }
        }
        if (credentials.value.identity != refreshed.identity) throw changed()
        val retried = transport(path, method, access = refreshed.accessToken ?: throw required(), configure = configure)
        if (credentials.value.identity != refreshed.identity) throw changed()
        if (retried.status.value == 401) {
            mutex.withLock {
                withContext(NonCancellable) {
                    if (credentials.value.identity == refreshed.identity && credentials.value.accessToken == refreshed.accessToken) {
                        invalidateLocked(LOST_SESSION)
                    }
                }
            }
        }
        if (retried.status.value !in 200..299) throw responseError(retried)
        return retried
    }

    private suspend fun createNewLocked(): LoginDto {
        credentials.value = Credentials(identity = credentials.value.identity + 1)
        val response = authResponse("/api/auth/login", null)
        val result = readLogin(response)
        if (!result.newMember) throw invalidResponse()
        persistLogin(result, readRefreshCookie(response))
        return result
    }

    private suspend fun renewLocked(current: Credentials): LoginDto {
        var active = current
        var saved = active.stored ?: throw required()
        requireRestorable(saved)
        try {
            pendingLogin?.let { (result, cookie) ->
                if (result.member.memberId != saved.memberId) throw invalidResponse()
                persistLogin(result, cookie)
                // 401 경로에서는 저장을 기다린 Access Token이 이미 만료됐을 수 있다.
                // 교체된 쿠키를 먼저 보존한 다음 새 Access Token을 받아 재시도한다.
                active = credentials.value
                saved = active.stored ?: throw required()
            }
            val response = authResponse("/api/auth/refresh", saved.refreshToken)
            val result = readLogin(response)
            if (result.newMember || result.member.memberId != saved.memberId) throw invalidResponse()
            persistLogin(result, readRefreshCookie(response))
            return result
        } catch (error: ApiException) {
            if (error.status == 401) {
                invalidateLocked(LOST_SESSION)
            } else {
                // 같은 401 묶음은 실패 결과도 공유한다. 새 사용자 동작은 다음 cycle에서 재시도할 수 있다.
                credentials.value = active.copy(refreshCycle = active.refreshCycle + 1, refreshFailure = error)
            }
            throw error
        }
    }

    private suspend fun persistLogin(result: LoginDto, refreshToken: String) {
        val saved = StoredSession(origin = origin, memberId = result.member.memberId, refreshToken = refreshToken)
        // 응답 쿠키가 교체되면 같은 보안 항목에 원자 저장을 마친 뒤 Access Token을 공개한다.
        // 저장 실패 시 발급받은 쿠키를 메모리에만 보존해 네트워크 재발급 없이 저장을 재시도한다.
        pendingLogin = result to refreshToken
        storage { store.write(json.encodeToString(saved)) }
        pendingLogin = null
        credentials.value = credentials.value.copy(stored = saved, accessToken = result.accessToken,
            refreshCycle = credentials.value.refreshCycle + 1, refreshFailure = null)
        mutableSession.value = AuthSession.Authenticated(MemberDto(result.member.memberId))
    }

    private suspend fun invalidateLocked(reason: String) {
        pendingLogin = null
        val previous = credentials.value
        credentials.value = Credentials(identity = previous.identity + 1)
        try {
            val marker = StoredSession(origin = origin, memberId = previous.stored?.memberId, requiresNewSessionConfirmation = true)
            storage { store.write(json.encodeToString(marker)) }
            mutableSession.value = AuthSession.LoggedOut(reason, requiresNewSessionConfirmation = true)
        } catch (error: ApiException) {
            mutableSession.value = AuthSession.LoggedOut(error.message, canRetry = true, requiresNewSessionConfirmation = true)
            throw error
        }
    }

    private suspend fun readStored(): StoredSession? {
        val raw = storage { store.read() } ?: return null
        val saved = try { json.decodeFromString<StoredSession>(raw) }
        catch (_: SerializationException) { throw legacy() }
        catch (_: IllegalArgumentException) { throw legacy() }
        if (saved.version != 2 || saved.origin != origin) throw legacy()
        return saved
    }

    private fun requireRestorable(saved: StoredSession) {
        if (saved.requiresNewSessionConfirmation) throw ApiException(401, "SESSION_RESET_REQUIRED", LOST_SESSION)
        if (saved.memberId == null || saved.memberId <= 0 || saved.refreshToken.isNullOrBlank() || !validCookieValue(saved.refreshToken)) {
            throw legacy()
        }
    }

    private fun showRecoveryFailure(error: ApiException) {
        if (mutableSession.value is AuthSession.Authenticated) return
        val confirmation = error.code in setOf("SESSION_RESET_REQUIRED", "LEGACY_SESSION", "REFRESH_TOKEN_INVALID")
        if (mutableSession.value is AuthSession.LoggedOut &&
            (mutableSession.value as AuthSession.LoggedOut).requiresNewSessionConfirmation) return
        mutableSession.value = AuthSession.LoggedOut(error.message, canRetry = !confirmation, requiresNewSessionConfirmation = confirmation)
    }

    private suspend fun authResponse(path: String, refreshToken: String?): HttpResponse = try {
        withTimeout(30_000) {
            val response = transport(path, HttpMethod.Post, refreshToken = refreshToken)
            if (response.status.value !in 200..299) throw responseError(response)
            response
        }
    } catch (error: TimeoutCancellationException) {
        throw ApiException(0, "NETWORK_ERROR", "인증 서버 응답이 지연됩니다. 잠시 후 다시 시도해 주세요.", cause = error)
    }

    private suspend fun readLogin(response: HttpResponse): LoginDto {
        val envelope = try { json.decodeFromString<ApiEnvelope<LoginDto>>(response.bodyAsText()) }
        catch (error: SerializationException) { throw invalidResponse(error) }
        val result = envelope.data
        if (envelope.code != "SUCCESS" || result.accessToken.isBlank() || result.tokenType != "Bearer" ||
            result.expiresIn <= 0 || result.member.memberId <= 0) throw invalidResponse()
        return result
    }

    private fun readRefreshCookie(response: HttpResponse): String {
        val cookie = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
            .mapNotNull { runCatching { parseServerSetCookieHeader(it) }.getOrNull() }
            .lastOrNull { it.name == "refresh_token" } ?: throw invalidResponse()
        val domain = cookie.domain?.removePrefix(".")?.lowercase()
        if (!cookie.secure || !cookie.httpOnly || cookie.path != "/api/auth" ||
            (domain != null && domain != Url(origin).host.lowercase()) ||
            (cookie.maxAge != null && cookie.maxAge!! <= 0) || !validCookieValue(cookie.value)) throw invalidResponse()
        return cookie.value
    }

    private suspend fun transport(
        path: String,
        method: HttpMethod,
        access: String? = null,
        refreshToken: String? = null,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = try {
        check(refreshToken == null || path == "/api/auth/refresh")
        client.request(origin + path) {
            this.method = method
            expectSuccess = false
            access?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            refreshToken?.let { header(HttpHeaders.Cookie, "refresh_token=$it") }
            configure()
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { throw ApiException(0, "NETWORK_ERROR", "서버에 연결하지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요.", cause = error) }

    private suspend fun responseError(response: HttpResponse): ApiException {
        val error = try { json.decodeFromString<ErrorEnvelope>(response.bodyAsText()) } catch (_: SerializationException) { null }
        return ApiException(response.status.value, error?.code ?: "HTTP_${response.status.value}",
            error?.message?.takeIf { it.isNotBlank() } ?: "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", error?.errors ?: emptyList())
    }

    private suspend fun <T> storage(operation: suspend () -> T): T = try { operation() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { throw ApiException(0, "SECURE_STORAGE_ERROR", "보안 저장소를 읽거나 저장하지 못했습니다. 기존 기록을 보호하기 위해 다시 시도해 주세요.", cause = error) }

    private fun validCookieValue(value: String): Boolean = value.length in 1..4096 && Regex("[A-Za-z0-9._~+/=-]+").matches(value)
    private fun required() = ApiException(401, "AUTH_REQUIRED", "기존 기록의 연결을 다시 확인해 주세요.")
    private fun changed() = ApiException(401, "SESSION_CHANGED", "기록 공간이 변경되었습니다. 다시 요청해 주세요.")
    private fun legacy() = ApiException(401, "LEGACY_SESSION", "이전에 저장된 로그인 정보는 현재 방식으로 복원할 수 없습니다. 새 기록을 시작하기 전에 확인해 주세요.")
    private fun invalidResponse(cause: Throwable? = null) = ApiException(0, "INVALID_RESPONSE", "인증 서버 응답을 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.", cause = cause)

    companion object {
        private const val LOST_SESSION = "이 기기의 익명 기록 연결이 만료되었거나 해제되었습니다. 이전 기록을 복원할 수 없으며 새 기록은 확인 후 시작할 수 있습니다."
    }
}
