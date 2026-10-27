package kr.jachwisunbae.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiClientTest {
    @Test
    fun firstBootstrapCreatesAnonymousMemberAndStoresOnlyRefreshCredential() = runTest {
        val store = MemoryTokenStore()
        lateinit var api: ApiClient
        val engine = mockEngine { request ->
            assertIs<AuthSession.Loading>(api.session.value)
            assertEquals("/api/auth/login", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            respond(loginJson(newMember = true), headers = cookieHeaders("refresh-one"))
        }
        HttpClient(engine).use { client ->
            api = ApiClient(client, store, baseUrl)
            api.bootstrapSession()
            assertEquals(MemberDto(7), assertIs<AuthSession.Authenticated>(api.session.value).member)
            val saved = Json.decodeFromString<StoredSession>(store.token!!)
            assertEquals("refresh-one", saved.refreshToken)
            assertEquals(baseUrl, saved.origin)
            assertEquals(7L, saved.memberId)
            assertFalse(store.token!!.contains("access-one"))
            assertFalse(store.token!!.contains("accessToken"))
        }
    }

    @Test
    fun restoreUsesRefreshOnlyAndPersistsRotatedCookie() = runTest {
        val store = MemoryTokenStore(stored())
        val paths = mutableListOf<String>()
        val engine = mockEngine { request ->
            paths += request.url.encodedPath
            if (request.url.encodedPath == "/api/auth/refresh") {
                assertEquals("refresh_token=refresh-one", request.headers[HttpHeaders.Cookie])
                assertNull(request.headers[HttpHeaders.Authorization])
                respond(loginJson("access-two"), headers = cookieHeaders("refresh-two"))
            } else {
                assertEquals("Bearer access-two", request.headers[HttpHeaders.Authorization])
                assertNull(request.headers[HttpHeaders.Cookie])
                respond(emptyProperties, headers = jsonHeaders)
            }
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            assertIs<AuthSession.Authenticated>(api.session.value)
            assertEquals("refresh-two", Json.decodeFromString<StoredSession>(store.token!!).refreshToken)
            assertEquals(PropertyListDto(0, emptyList()), api.properties())
            assertEquals(listOf("/api/auth/refresh", "/api/properties"), paths)
        }
    }

    @Test
    fun callingLoginWithExistingCookieNeverUsesCreateEndpoint() = runTest {
        HttpClient(mockEngine { request ->
            assertEquals("/api/auth/refresh", request.url.encodedPath)
            respond(loginJson(), headers = cookieHeaders())
        }).use { client ->
            assertFalse(ApiClient(client, MemoryTokenStore(stored()), baseUrl).login().newMember)
        }
    }

    @Test
    fun emptyRestoreMakesNoNetworkRequest() = runTest {
        HttpClient(mockEngine { error("빈 저장소 복원은 회원을 만들면 안 됩니다.") }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(), baseUrl)
            api.restoreSession()
            assertEquals(AuthSession.LoggedOut(), api.session.value)
            assertEquals("AUTH_REQUIRED", assertFailsWith<ApiException> { api.properties() }.code)
        }
    }

    @Test
    fun legacyAccessOnlyValueIsPreservedAndRequiresExplicitNewSession() = runTest {
        val store = MemoryTokenStore("legacy-access-fixture")
        HttpClient(mockEngine { error("구 인증정보로 자동 신규 회원을 만들면 안 됩니다.") }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.bootstrapSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).requiresNewSessionConfirmation)
            assertEquals("legacy-access-fixture", store.token)
            assertEquals(0, store.writes)
            assertEquals(0, store.clears)
            assertEquals("LEGACY_SESSION", assertFailsWith<ApiException> { api.login() }.code)
        }
    }

    @Test
    fun credentialFromDifferentOriginIsNeverSentOrOverwritten() = runTest {
        val original = stored(origin = "https://another.example.test")
        val store = MemoryTokenStore(original)
        HttpClient(mockEngine { error("다른 환경 쿠키 전송 금지") }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.bootstrapSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).requiresNewSessionConfirmation)
            assertEquals(original, store.token)
        }
    }

    @Test
    fun invalidRefreshPersistsConfirmationAcrossRestartWithoutCreatingMember() = runTest {
        val store = MemoryTokenStore(stored())
        var requests = 0
        HttpClient(mockEngine { request ->
            requests++
            assertEquals("/api/auth/refresh", request.url.encodedPath)
            respond(refreshError, HttpStatusCode.Unauthorized, jsonHeaders)
        }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.bootstrapSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).requiresNewSessionConfirmation)
            val marker = Json.decodeFromString<StoredSession>(store.token!!)
            assertNull(marker.refreshToken)
            assertTrue(marker.requiresNewSessionConfirmation)
            val restarted = ApiClient(client, store, baseUrl)
            restarted.bootstrapSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(restarted.session.value).requiresNewSessionConfirmation)
            assertEquals(1, requests)
        }
    }

    @Test
    fun offlineRestoreRetainsCredentialAndRetryRecoversSameMember() = runTest {
        val original = stored()
        val store = MemoryTokenStore(original)
        var offline = true
        HttpClient(mockEngine { request ->
            assertEquals("/api/auth/refresh", request.url.encodedPath)
            if (offline) error("transport unavailable")
            respond(loginJson(), headers = cookieHeaders())
        }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.bootstrapSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).canRetry)
            assertEquals(original, store.token)
            assertEquals(0, store.writes)
            offline = false
            api.bootstrapSession()
            assertEquals(7L, assertIs<AuthSession.Authenticated>(api.session.value).member.id)
        }
    }

    @Test
    fun serverRefreshFailureDoesNotCreateAnonymousAccountOrClearCookie() = runTest {
        val original = stored()
        val store = MemoryTokenStore(original)
        HttpClient(mockEngine { request ->
            assertEquals("/api/auth/refresh", request.url.encodedPath)
            respond("""{"code":"INTERNAL_SERVER_ERROR","message":"서버 오류","errors":[]}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.bootstrapSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).canRetry)
            assertEquals(original, store.token)
        }
    }

    @Test
    fun unreadableSecureStorageNeverCreatesWritesOrClearsAnything() = runTest {
        var writes = 0
        var clears = 0
        val store = object : TokenStore {
            override suspend fun read(): String? = error("secure storage locked")
            override suspend fun write(token: String) { writes++ }
            override suspend fun clear() { clears++ }
        }
        HttpClient(mockEngine { error("저장소 오류 뒤 회원 생성 금지") }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.bootstrapSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).canRetry)
            assertEquals(0, writes)
            assertEquals(0, clears)
        }
    }

    @Test
    fun failedRefreshPersistenceDoesNotPublishAccessToken() = runTest {
        val store = object : TokenStore {
            override suspend fun read(): String? = null
            override suspend fun write(token: String) { error("secure storage locked") }
            override suspend fun clear() = Unit
        }
        HttpClient(mockEngine { respond(loginJson(newMember = true), headers = cookieHeaders()) }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.bootstrapSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).canRetry)
            assertEquals("AUTH_REQUIRED", assertFailsWith<ApiException> { api.properties() }.code)
        }
    }

    @Test
    fun storageRetryPersistsAlreadyIssuedCredentialWithoutCreatingAnotherMember() = runTest {
        var writable = false
        var persisted: String? = null
        var requests = 0
        val store = object : TokenStore {
            override suspend fun read() = persisted
            override suspend fun write(token: String) {
                check(writable) { "storage temporarily locked" }
                persisted = token
            }
            override suspend fun clear() { persisted = null }
        }
        HttpClient(mockEngine {
            requests++
            respond(loginJson(newMember = true), headers = cookieHeaders("issued-once"))
        }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.bootstrapSession()
            assertIs<AuthSession.LoggedOut>(api.session.value)
            writable = true
            api.bootstrapSession()
            assertIs<AuthSession.Authenticated>(api.session.value)
            assertEquals("issued-once", Json.decodeFromString<StoredSession>(persisted!!).refreshToken)
            assertEquals(1, requests)
        }
    }

    @Test
    fun rotatedCookieSurvivesTransientPersistenceFailureInMemoryForRetry() = runTest {
        var writable = true
        var persisted = stored()
        var requests = 0
        val store = object : TokenStore {
            override suspend fun read() = persisted
            override suspend fun write(token: String) {
                check(writable) { "storage temporarily locked" }
                persisted = token
            }
            override suspend fun clear() = Unit
        }
        HttpClient(mockEngine {
            requests++
            respond(loginJson("access-$requests"), headers = cookieHeaders("refresh-$requests"))
        }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            writable = false
            assertEquals("SECURE_STORAGE_ERROR", assertFailsWith<ApiException> { api.refresh() }.code)
            writable = true
            api.refresh()
            assertEquals("refresh-2", Json.decodeFromString<StoredSession>(persisted).refreshToken)
            assertEquals(2, requests)
        }
    }

    @Test
    fun explicitNewSessionStorageRetryPreservesIssuedMemberAndShowsRetryState() = runTest {
        var writable = false
        var persisted = "legacy-access-token"
        var requests = 0
        val store = object : TokenStore {
            override suspend fun read() = persisted
            override suspend fun write(token: String) {
                check(writable) { "storage temporarily locked" }
                persisted = token
            }
            override suspend fun clear() = Unit
        }
        lateinit var api: ApiClient
        HttpClient(mockEngine { request ->
            assertIs<AuthSession.Loading>(api.session.value)
            assertEquals("/api/auth/login", request.url.encodedPath)
            assertNull(request.headers[HttpHeaders.Cookie])
            requests++
            respond(loginJson(memberId = 9, newMember = true), headers = cookieHeaders("new-credential"))
        }).use { client ->
            api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).requiresNewSessionConfirmation)
            assertEquals("SECURE_STORAGE_ERROR", assertFailsWith<ApiException> { api.startNewSession() }.code)
            val failed = assertIs<AuthSession.LoggedOut>(api.session.value)
            assertTrue(failed.canRetry)
            assertFalse(failed.requiresNewSessionConfirmation)
            assertEquals("legacy-access-token", persisted)
            writable = true
            assertEquals(9L, api.startNewSession().member.memberId)
            assertEquals(MemberDto(9), assertIs<AuthSession.Authenticated>(api.session.value).member)
            assertEquals("new-credential", Json.decodeFromString<StoredSession>(persisted).refreshToken)
            assertEquals(1, requests)
        }
    }

    @Test
    fun unauthorizedRetryUsesFreshAccessAfterSavingAnOlderPendingCookie() = runTest {
        var writable = true
        var persisted = stored()
        var refreshes = 0
        val store = object : TokenStore {
            override suspend fun read() = persisted
            override suspend fun write(token: String) {
                check(writable) { "storage temporarily locked" }
                persisted = token
            }
            override suspend fun clear() = Unit
        }
        HttpClient(mockEngine { request ->
            if (request.url.encodedPath == "/api/auth/refresh") {
                refreshes++
                if (refreshes == 3) assertEquals("refresh_token=rotated-2", request.headers[HttpHeaders.Cookie])
                respond(loginJson("access-$refreshes"), headers = cookieHeaders("rotated-$refreshes"))
            } else if (request.headers[HttpHeaders.Authorization] == "Bearer access-3") {
                respond(emptyProperties, headers = jsonHeaders)
            } else {
                respond(accessError, HttpStatusCode.Unauthorized, jsonHeaders)
            }
        }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            writable = false
            assertEquals("SECURE_STORAGE_ERROR", assertFailsWith<ApiException> { api.properties() }.code)
            writable = true
            assertTrue(api.properties().items.isEmpty())
            assertIs<AuthSession.Authenticated>(api.session.value)
            assertEquals("rotated-3", Json.decodeFromString<StoredSession>(persisted).refreshToken)
            assertEquals(3, refreshes)
        }
    }

    @Test
    fun missingRefreshCookieDoesNotTreatLoginAsSuccess() = runTest {
        val store = MemoryTokenStore()
        HttpClient(mockEngine { respond(loginJson(newMember = true), headers = jsonHeaders) }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            assertEquals("INVALID_RESPONSE", assertFailsWith<ApiException> { api.login() }.code)
            assertNull(store.token)
            assertEquals("AUTH_REQUIRED", assertFailsWith<ApiException> { api.properties() }.code)
        }
    }

    @Test
    fun rejectsCrossDomainOrInsecureRefreshCookie() = runTest {
        for (cookie in listOf(
            "refresh_token=fixture; Path=/api/auth; HttpOnly",
            "refresh_token=fixture; Path=/; HttpOnly; Secure",
            "refresh_token=fixture; Path=/api/auth; HttpOnly; Secure; Domain=other.example.test",
            "refresh_token=fixture; Path=/api/auth; HttpOnly; Secure; Max-Age=0",
        )) {
            val store = MemoryTokenStore()
            HttpClient(mockEngine { respond(loginJson(newMember = true), headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.SetCookie to listOf(cookie))) }).use { client ->
                val api = ApiClient(client, store, baseUrl)
                assertEquals("INVALID_RESPONSE", assertFailsWith<ApiException> { api.login() }.code)
                assertNull(store.token)
            }
        }
    }

    @Test
    fun refreshCannotSilentlySwitchMember() = runTest {
        val original = stored()
        val store = MemoryTokenStore(original)
        HttpClient(mockEngine { respond(loginJson(memberId = 99), headers = cookieHeaders("unexpected-cookie")) }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            assertIs<AuthSession.LoggedOut>(api.session.value)
            assertEquals(original, store.token)
        }
    }

    @Test
    fun concurrent401sShareOneRefreshAndRetryEachRequestOnlyOnce() = runTest {
        var refreshes = 0
        var oldRequests = 0
        var retriedRequests = 0
        val bothStarted = CompletableDeferred<Unit>()
        val engine = mockEngine { request ->
            when {
                request.url.encodedPath == "/api/auth/refresh" -> {
                    refreshes++
                    respond(loginJson(if (refreshes == 1) "expired-access" else "fresh-access"), headers = cookieHeaders(if (refreshes == 1) "refresh-one" else "refresh-two"))
                }
                request.headers[HttpHeaders.Authorization] == "Bearer expired-access" -> {
                    assertNull(request.headers[HttpHeaders.Cookie])
                    oldRequests++
                    if (oldRequests == 2) bothStarted.complete(Unit)
                    bothStarted.await()
                    respond(accessError, HttpStatusCode.Unauthorized, jsonHeaders)
                }
                else -> {
                    retriedRequests++
                    assertEquals("Bearer fresh-access", request.headers[HttpHeaders.Authorization])
                    assertNull(request.headers[HttpHeaders.Cookie])
                    respond(emptyProperties, headers = jsonHeaders)
                }
            }
        }
        val store = MemoryTokenStore(stored())
        HttpClient(engine).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            val first = async { api.properties() }
            val second = async { api.properties() }
            assertEquals(0, first.await().totalCount)
            assertEquals(0, second.await().totalCount)
            assertEquals(2, refreshes)
            assertEquals(2, oldRequests)
            assertEquals(2, retriedRequests)
            assertEquals("refresh-two", Json.decodeFromString<StoredSession>(store.token!!).refreshToken)
        }
    }

    @Test
    fun concurrent401sAlsoShareFailedRefreshWithoutRepeatedNetworkCalls() = runTest {
        var refreshes = 0
        var oldRequests = 0
        val bothStarted = CompletableDeferred<Unit>()
        val engine = mockEngine { request ->
            if (request.url.encodedPath == "/api/auth/refresh") {
                refreshes++
                if (refreshes > 1) error("offline during refresh")
                respond(loginJson(), headers = cookieHeaders())
            } else {
                oldRequests++
                if (oldRequests == 2) bothStarted.complete(Unit)
                bothStarted.await()
                respond(accessError, HttpStatusCode.Unauthorized, jsonHeaders)
            }
        }
        val store = MemoryTokenStore(stored())
        HttpClient(engine).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            val first = async { assertFailsWith<ApiException> { api.properties() } }
            val second = async { assertFailsWith<ApiException> { api.properties() } }
            assertEquals("NETWORK_ERROR", first.await().code)
            assertEquals("NETWORK_ERROR", second.await().code)
            assertEquals(2, refreshes)
            assertIs<AuthSession.Authenticated>(api.session.value)
            assertEquals("refresh-one", Json.decodeFromString<StoredSession>(store.token!!).refreshToken)
        }
    }

    @Test
    fun second401StopsRetryAndRequiresConfirmation() = runTest {
        var refreshes = 0
        var protectedRequests = 0
        HttpClient(mockEngine { request ->
            if (request.url.encodedPath == "/api/auth/refresh") {
                refreshes++
                respond(loginJson("access-$refreshes"), headers = cookieHeaders())
            } else {
                protectedRequests++
                respond(accessError, HttpStatusCode.Unauthorized, jsonHeaders)
            }
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            assertFailsWith<ApiException> { api.properties() }
            assertEquals(2, refreshes)
            assertEquals(2, protectedRequests)
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).requiresNewSessionConfirmation)
        }
    }

    @Test
    fun forbiddenDoesNotRefreshOrClearAuthentication() = runTest {
        var refreshes = 0
        HttpClient(mockEngine { request ->
            if (request.url.encodedPath == "/api/auth/refresh") {
                refreshes++
                respond(loginJson(), headers = cookieHeaders())
            } else respond("""{"code":"ACCESS_DENIED","message":"권한이 없습니다.","errors":[]}""", HttpStatusCode.Forbidden, jsonHeaders)
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            assertEquals(403, assertFailsWith<ApiException> { api.property(9) }.status)
            assertEquals(1, refreshes)
            assertIs<AuthSession.Authenticated>(api.session.value)
        }
    }

    @Test
    fun logoutRemovesRefreshSecretButKeepsConfirmationMarkerAcrossRestart() = runTest {
        val store = MemoryTokenStore(stored())
        var requests = 0
        HttpClient(mockEngine {
            requests++
            respond(loginJson(), headers = cookieHeaders())
        }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            api.logout()
            assertFalse(store.token!!.contains("refresh-one"))
            val restarted = ApiClient(client, store, baseUrl)
            restarted.bootstrapSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(restarted.session.value).requiresNewSessionConfirmation)
            assertEquals(1, requests)
        }
    }

    @Test
    fun explicitNewSessionDoesNotSendOldCookieAndReplacesStoredMember() = runTest {
        val store = MemoryTokenStore(stored())
        HttpClient(mockEngine { request ->
            assertEquals("/api/auth/login", request.url.encodedPath)
            assertNull(request.headers[HttpHeaders.Cookie])
            assertNull(request.headers[HttpHeaders.Authorization])
            respond(loginJson("new-access", 99, true), headers = cookieHeaders("new-refresh"))
        }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.startNewSession()
            assertEquals(99L, assertIs<AuthSession.Authenticated>(api.session.value).member.id)
            assertEquals("new-refresh", Json.decodeFromString<StoredSession>(store.token!!).refreshToken)
        }
    }

    @Test
    fun delayedOld401CannotRefreshOrReplayUnderDifferentMember() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        HttpClient(mockEngine { request ->
            requests++
            when (request.url.encodedPath) {
                "/api/auth/refresh" -> respond(loginJson(), headers = cookieHeaders())
                "/api/auth/login" -> respond(loginJson("new-access", 99, true), headers = cookieHeaders("new-refresh"))
                else -> { started.complete(Unit); release.await(); respond(accessError, HttpStatusCode.Unauthorized, jsonHeaders) }
            }
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            val old = async { assertFailsWith<ApiException> { api.deleteProperty(10) } }
            started.await()
            api.logout()
            api.startNewSession()
            release.complete(Unit)
            assertEquals("SESSION_CHANGED", old.await().code)
            assertEquals(99L, assertIs<AuthSession.Authenticated>(api.session.value).member.id)
            assertEquals(3, requests)
        }
    }

    @Test
    fun delayedSuccessFromPreviousMemberIsNotReturnedAfterSessionSwitch() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        HttpClient(mockEngine { request ->
            when (request.url.encodedPath) {
                "/api/auth/refresh" -> respond(loginJson(), headers = cookieHeaders())
                "/api/auth/login" -> respond(loginJson("new-access", 99, true), headers = cookieHeaders("new-refresh"))
                else -> { started.complete(Unit); release.await(); respond(emptyProperties, headers = jsonHeaders) }
            }
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            val previous = async { assertFailsWith<ApiException> { api.properties() } }
            started.await()
            api.startNewSession()
            release.complete(Unit)
            assertEquals("SESSION_CHANGED", previous.await().code)
        }
    }

    @Test
    fun logoutFinishesPersistenceWhenCallerIsCancelled() = runTest {
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        var raw: String? = stored()
        var blockWrites = false
        val store = object : TokenStore {
            override suspend fun read() = raw
            override suspend fun write(token: String) {
                if (blockWrites) { writeStarted.complete(Unit); finishWrite.await() }
                raw = token
            }
            override suspend fun clear() { raw = null }
        }
        HttpClient(mockEngine { respond(loginJson(), headers = cookieHeaders()) }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            blockWrites = true
            val logout = launch { api.logout() }
            writeStarted.await()
            logout.cancel()
            finishWrite.complete(Unit)
            logout.join()
            assertNull(Json.decodeFromString<StoredSession>(raw!!).refreshToken)
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).requiresNewSessionConfirmation)
        }
    }

    @Test
    fun requestCancellationIsNotNetworkFailure() = runTest {
        HttpClient(mockEngine { request ->
            if (request.url.encodedPath == "/api/auth/refresh") respond(loginJson(), headers = cookieHeaders())
            else throw CancellationException("cancelled request")
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            assertFailsWith<CancellationException> { api.properties() }
        }
    }

    @Test
    fun invalidSuccessEnvelopeIsNotTreatedAsEmptyList() = runTest {
        HttpClient(mockEngine { request ->
            if (request.url.encodedPath == "/api/auth/refresh") respond(loginJson(), headers = cookieHeaders())
            else respond("""{"code":"SUCCESS","message":"ok","data":{"totalCount":0}}""", headers = jsonHeaders)
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            assertEquals("INVALID_RESPONSE", assertFailsWith<ApiException> { api.properties() }.code)
        }
    }

    @Test
    fun errorDetailsMemoReplacementAndDeleteNoContentRetainWireContract() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        HttpClient(mockEngine { request ->
            requests += request
            when {
                request.url.encodedPath == "/api/auth/refresh" -> respond(loginJson(), headers = cookieHeaders())
                request.method == HttpMethod.Delete -> respond("", HttpStatusCode.NoContent)
                request.url.encodedPath.endsWith("/memo") -> {
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("""{"freeMemo":""}""", (request.body as TextContent).text)
                    respond("""{"code":"SUCCESS","message":"저장","data":{"propertyId":9,"freeMemo":""}}""", headers = jsonHeaders)
                }
                else -> respond("""{"code":"INVALID_REQUEST","message":"입력 오류","errors":[{"field":"name","reason":"필수입니다."}]}""", HttpStatusCode.BadRequest, jsonHeaders)
            }
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            assertEquals("", api.saveMemo(9, "").freeMemo)
            api.deleteProperty(9)
            assertEquals(listOf(FieldErrorDto("name", "필수입니다.")), assertFailsWith<ApiException> { api.property(9) }.errors)
            assertEquals(4, requests.size)
        }
    }

    @Test
    fun pdfUsesSelectedIdsAndRejectsHtmlContent() = runTest {
        var returnPdf = true
        HttpClient(mockEngine { request ->
            if (request.url.encodedPath == "/api/auth/refresh") respond(loginJson(), headers = cookieHeaders())
            else {
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/api/properties/export.pdf", request.url.encodedPath)
                assertEquals("""{"propertyIds":[3,9]}""", (request.body as TextContent).text)
                assertNull(request.headers[HttpHeaders.Cookie])
                if (returnPdf) respond("%PDF-1.7 test", headers = headersOf(HttpHeaders.ContentType, "application/pdf"))
                else respond("<html>proxy failure</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
            }
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            assertContentEquals("%PDF-1.7 test".encodeToByteArray(), api.comparisonPdf(listOf(3, 9)))
            returnPdf = false
            assertEquals("INVALID_RESPONSE", assertFailsWith<ApiException> { api.comparisonPdf(listOf(3, 9)) }.code)
        }
    }

    @Test
    fun comparisonAndPhotoLimitsFailBeforeNetworkMutation() = runTest {
        HttpClient(mockEngine { error("유효하지 않은 요청은 전송하지 않습니다.") }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(), baseUrl)
            assertEquals("CLIENT_VALIDATION", assertFailsWith<ApiException> { api.comparisonPdf(listOf(3, 3)) }.code)
            assertEquals("CLIENT_VALIDATION", assertFailsWith<ApiException> { api.uploadPhoto(3, byteArrayOf(), "photo.jpg", "image/jpeg") }.code)
        }
    }

    @Test
    fun nearbyUsesCurrentCategoryContract() = runTest {
        HttpClient(mockEngine { request ->
            if (request.url.encodedPath == "/api/auth/refresh") respond(loginJson(), headers = cookieHeaders())
            else {
                assertEquals("500", request.url.parameters["radius"])
                assertEquals("HOSPITAL,CONVENIENCE", request.url.parameters["categories"])
                respond("""{"code":"SUCCESS","message":"ok","data":{"center":{"latitude":37.5,"longitude":127.0},"radius":500,"counts":{"HOSPITAL":0,"CONVENIENCE":0},"places":[]}}""", headers = jsonHeaders)
            }
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            assertTrue(api.nearby(37.5, 127.0, 500, listOf(MapCategory.HOSPITAL, MapCategory.CONVENIENCE)).places.isEmpty())
            assertEquals(5, MapCategory.entries.size)
        }
    }

    @Test
    fun transportUsesBusStopEndpointAndMergesWithOrdinaryNearbyFacilities() = runTest {
        val paths = mutableListOf<String>()
        HttpClient(mockEngine { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/api/auth/refresh" -> respond(loginJson(), headers = cookieHeaders())
                "/api/maps/nearby" -> {
                    assertEquals("HOSPITAL", request.url.parameters["categories"])
                    respond("""{"code":"SUCCESS","message":"ok","data":{"center":{"latitude":37.5,"longitude":127.0},"radius":500,"counts":{"HOSPITAL":1},"places":[{"providerPlaceId":"hospital-1","name":"병원","category":"HOSPITAL","address":"주소","latitude":37.51,"longitude":127.0,"distanceMeters":100}]}}""", headers = jsonHeaders)
                }
                "/api/maps/bus-stops" -> {
                    assertNull(request.headers[HttpHeaders.Cookie])
                    assertEquals("Bearer access-one", request.headers[HttpHeaders.Authorization])
                    assertNull(request.url.parameters["categories"])
                    assertEquals("500", request.url.parameters["radius"])
                    respond(busStopJson, headers = jsonHeaders)
                }
                else -> error("예상하지 않은 API 경로")
            }
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            val combined = api.nearby(37.5, 127.0, categories = listOf(MapCategory.HOSPITAL, MapCategory.TRANSPORT))
            assertEquals(mapOf(MapCategory.HOSPITAL to 1, MapCategory.TRANSPORT to 2), combined.counts)
            assertEquals(listOf("hospital-1", "stop-1", "stop-2"), combined.places.map { it.providerPlaceId })
            assertEquals(listOf("정류장", "정류장"), combined.places.filter { it.category == MapCategory.TRANSPORT }.map { it.name })
            paths.clear()
            assertEquals(2, api.nearby(37.5, 127.0, categories = listOf(MapCategory.TRANSPORT)).places.size)
            assertEquals(listOf("/api/maps/bus-stops"), paths)
        }
    }

    @Test
    fun busStopFailureIsNotReportedAsAnEmptyTransportResult() = runTest {
        HttpClient(mockEngine { request ->
            if (request.url.encodedPath == "/api/auth/refresh") respond(loginJson(), headers = cookieHeaders())
            else respond("""{"code":"BUS_STOP_PROVIDER_UNAVAILABLE","message":"정류장 조회 지연"}""", HttpStatusCode.ServiceUnavailable, jsonHeaders)
        }).use { client ->
            val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
            api.restoreSession()
            assertEquals("BUS_STOP_PROVIDER_UNAVAILABLE", assertFailsWith<ApiException> {
                api.nearby(37.5, 127.0, categories = listOf(MapCategory.TRANSPORT))
            }.code)
        }
    }

    @Test
    fun busStopsRejectInconsistentCountOrDuplicateNodeIds() = runTest {
        for (body in listOf(busStopJson.replace("\"count\":2", "\"count\":3"), busStopJson.replace("stop-2", "stop-1"))) {
            HttpClient(mockEngine { request ->
                if (request.url.encodedPath == "/api/auth/refresh") respond(loginJson(), headers = cookieHeaders())
                else respond(body, headers = jsonHeaders)
            }).use { client ->
                val api = ApiClient(client, MemoryTokenStore(stored()), baseUrl)
                api.restoreSession()
                assertEquals("INVALID_RESPONSE", assertFailsWith<ApiException> { api.busStops(37.5, 127.0) }.code)
            }
        }
    }

    @Test
    fun rejectsInsecureOrCredentialBearingApiOrigins() {
        HttpClient(MockEngine { respond("") }).use { client ->
            assertFailsWith<IllegalArgumentException> { ApiClient(client, MemoryTokenStore(), "http://example.test") }
            assertFailsWith<IllegalArgumentException> { ApiClient(client, MemoryTokenStore(), "https://user:secret@example.test") }
            assertFailsWith<IllegalArgumentException> { ApiClient(client, MemoryTokenStore(), "https://example.test/api") }
        }
    }

    companion object {
        const val baseUrl = "https://example.test"
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        const val emptyProperties = """{"code":"SUCCESS","message":"ok","data":{"totalCount":0,"items":[]}}"""
        const val accessError = """{"code":"ACCESS_TOKEN_INVALID","message":"Access 인증 오류","errors":[]}"""
        const val refreshError = """{"code":"REFRESH_TOKEN_INVALID","message":"Refresh 인증 오류","errors":[]}"""
        const val busStopJson = """{"code":"SUCCESS","message":"ok","data":{"center":{"latitude":37.5,"longitude":127.0},"radius":500,"count":2,"busStops":[{"nodeId":"stop-1","name":"정류장","latitude":37.51,"longitude":127.0,"distanceMeters":100},{"nodeId":"stop-2","name":"정류장","latitude":37.49,"longitude":127.0,"distanceMeters":120}]}}"""
        fun loginJson(access: String = "access-one", memberId: Long = 7, newMember: Boolean = false) =
            """{"code":"SUCCESS","message":"ok","data":{"accessToken":"$access","tokenType":"Bearer","expiresIn":900,"newMember":$newMember,"member":{"memberId":$memberId}}}"""
        fun cookieHeaders(value: String = "refresh-one") = headersOf(
            HttpHeaders.ContentType to listOf("application/json"),
            HttpHeaders.SetCookie to listOf("refresh_token=$value; Path=/api/auth; Max-Age=31536000; Secure; HttpOnly; SameSite=Lax"),
        )
        fun stored(origin: String = baseUrl) = Json.encodeToString(StoredSession(origin = origin, memberId = 7, refreshToken = "refresh-one"))
    }
}

// 인증 withTimeout과 엔진 응답을 같은 가상 시간에서 실행해 실제 dispatcher와의 경쟁을 막는다.
private fun TestScope.mockEngine(handler: MockRequestHandler): MockEngine = MockEngine(
    MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler(handler)
    },
)

internal class MemoryTokenStore(var token: String? = null) : TokenStore {
    var writes = 0
    var clears = 0
    override suspend fun read(): String? = token
    override suspend fun write(token: String) { writes++; this.token = token }
    override suspend fun clear() { clears++; token = null }
}
