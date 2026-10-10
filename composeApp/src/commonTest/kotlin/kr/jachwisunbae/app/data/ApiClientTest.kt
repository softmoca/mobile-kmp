package kr.jachwisunbae.app.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiClientTest {
    @Test
    fun loginStoresTokenAndUsesDifferentMemberIdContract() = runTest {
        val store = MemoryTokenStore()
        val engine = MockEngine { request ->
            assertEquals("/api/auth/nickname", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertNull(request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("테스터", body.getValue("nickname").jsonPrimitive.content)
            assertEquals(" pass ", body.getValue("password").jsonPrimitive.content)
            respond(loginJson, headers = jsonHeaders)
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.login(" 테스터 ", " pass ")
            assertEquals("test-token", store.token)
            assertEquals(MemberDto(7, "테스터", true), assertIs<AuthSession.Authenticated>(api.session.value).member)
        }
    }

    @Test
    fun restoresMemberAndEmptyPropertiesWithBearerHeader() = runTest {
        val engine = MockEngine { request ->
            assertEquals("Bearer saved-token", request.headers[HttpHeaders.Authorization])
            respond(if (request.url.encodedPath == "/api/members/me") memberJson else emptyProperties, headers = jsonHeaders)
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, MemoryTokenStore("saved-token"), baseUrl)
            api.restoreSession()
            assertIs<AuthSession.Authenticated>(api.session.value)
            assertEquals(PropertyListDto(0, emptyList()), api.properties())
        }
    }

    @Test
    fun emptyTokenStoreDoesNotMakeNetworkRequest() = runTest {
        val engine = MockEngine { error("로그인 정보가 없으면 API를 호출하면 안 됩니다.") }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, MemoryTokenStore(), baseUrl)
            api.restoreSession()
            assertIs<AuthSession.LoggedOut>(api.session.value)
            assertEquals("AUTH_REQUIRED", assertFailsWith<ApiException> { api.properties() }.code)
        }
    }

    @Test
    fun unauthorizedExpiresSessionAndClearsSecureToken() = runTest {
        val store = MemoryTokenStore("saved-token")
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/members/me") respond(memberJson, headers = jsonHeaders)
            else respond("""{"code":"ACCESS_TOKEN_INVALID","message":"인증 정보를 확인할 수 없습니다.","errors":[]}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            val error = assertFailsWith<ApiException> { api.properties() }
            assertEquals(401, error.status)
            assertEquals("ACCESS_TOKEN_INVALID", error.code)
            assertNull(store.token)
            assertIs<AuthSession.LoggedOut>(api.session.value)
        }
    }

    @Test
    fun previousUnauthorizedResponseCannotExpireNewLogin() = runTest {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val finishFirstRequest = CompletableDeferred<Unit>()
        val store = MemoryTokenStore("old-token")
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/members/me" -> respond(memberJson, headers = jsonHeaders)
                "/api/auth/nickname" -> respond(loginJson, headers = jsonHeaders)
                else -> {
                    firstRequestStarted.complete(Unit)
                    finishFirstRequest.await()
                    respond("""{"code":"ACCESS_TOKEN_INVALID","message":"만료","errors":[]}""", HttpStatusCode.Unauthorized, jsonHeaders)
                }
            }
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            val oldRequest = async { assertFailsWith<ApiException> { api.properties() } }
            firstRequestStarted.await()
            api.login("테스터", "pass")
            finishFirstRequest.complete(Unit)
            oldRequest.await()
            assertEquals("test-token", store.token)
            assertIs<AuthSession.Authenticated>(api.session.value)
        }
    }

    @Test
    fun forbiddenDoesNotClearAuthentication() = runTest {
        val store = MemoryTokenStore("saved-token")
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/members/me") respond(memberJson, headers = jsonHeaders)
            else respond("""{"code":"ACCESS_DENIED","message":"권한이 없습니다.","errors":[]}""", HttpStatusCode.Forbidden, jsonHeaders)
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            assertEquals(403, assertFailsWith<ApiException> { api.property(9) }.status)
            assertEquals("saved-token", store.token)
            assertIs<AuthSession.Authenticated>(api.session.value)
        }
    }

    @Test
    fun offlineRestorePreservesTokenForRetry() = runTest {
        val store = MemoryTokenStore("saved-token")
        var offline = true
        val engine = MockEngine {
            if (offline) throw IllegalStateException("simulated transport outage")
            respond(memberJson, headers = jsonHeaders)
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            assertEquals("saved-token", store.token)
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).reason.orEmpty().contains("네트워크"))
            offline = false
            api.restoreSession()
            assertIs<AuthSession.Authenticated>(api.session.value)
        }
    }

    @Test
    fun unavailableSecureStorageDoesNotLeaveStartupLoading() = runTest {
        val store = object : TokenStore {
            override suspend fun read(): String? = error("secure storage locked")
            override suspend fun write(token: String) = Unit
            override suspend fun clear() = Unit
        }
        HttpClient(MockEngine { error("저장소 오류 뒤 API를 호출하지 않습니다.") }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            assertTrue(assertIs<AuthSession.LoggedOut>(api.session.value).reason.orEmpty().contains("보안 저장소"))
        }
    }

    @Test
    fun failedTokenPersistenceDoesNotAuthenticateSession() = runTest {
        val store = object : TokenStore {
            override suspend fun read(): String? = null
            override suspend fun write(token: String) { error("secure storage locked") }
            override suspend fun clear() = Unit
        }
        HttpClient(MockEngine { respond(loginJson, headers = jsonHeaders) }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            assertEquals("SECURE_STORAGE_ERROR", assertFailsWith<ApiException> { api.login("테스터") }.code)
            assertIs<AuthSession.LoggedOut>(api.session.value)
            assertEquals("AUTH_REQUIRED", assertFailsWith<ApiException> { api.properties() }.code)
        }
    }

    @Test
    fun logoutCompletesSecureClearEvenWhenCallingScreenIsCancelled() = runTest {
        val clearStarted = CompletableDeferred<Unit>()
        val finishClear = CompletableDeferred<Unit>()
        var savedToken: String? = "saved-token"
        val store = object : TokenStore {
            override suspend fun read() = savedToken
            override suspend fun write(token: String) { savedToken = token }
            override suspend fun clear() {
                clearStarted.complete(Unit)
                finishClear.await()
                savedToken = null
            }
        }
        HttpClient(MockEngine { respond(memberJson, headers = jsonHeaders) }).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            val logout = launch { api.logout() }
            clearStarted.await()
            assertIs<AuthSession.Authenticated>(api.session.value)
            logout.cancel()
            finishClear.complete(Unit)
            logout.join()
            assertNull(savedToken)
            assertIs<AuthSession.LoggedOut>(api.session.value)
        }
    }

    @Test
    fun cancellationIsNotPresentedAsNetworkError() = runTest {
        val engine = MockEngine { throw CancellationException("cancelled") }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, MemoryTokenStore(), baseUrl)
            assertFailsWith<CancellationException> { api.login("테스터") }
        }
    }

    @Test
    fun invalidSuccessEnvelopeIsNotTreatedAsEmptyList() = runTest {
        val engine = MockEngine { request ->
            respond(if (request.url.encodedPath == "/api/members/me") memberJson else """{"code":"SUCCESS","message":"ok","data":{"totalCount":0}}""", headers = jsonHeaders)
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, MemoryTokenStore("saved-token"), baseUrl)
            api.restoreSession()
            assertEquals("INVALID_RESPONSE", assertFailsWith<ApiException> { api.properties() }.code)
        }
    }

    @Test
    fun serverValidationDetailsArePreserved() = runTest {
        val engine = MockEngine {
            respond("""{"code":"INVALID_REQUEST","message":"입력 오류","errors":[{"field":"nickname","reason":"필수입니다."}]}""", HttpStatusCode.BadRequest, jsonHeaders)
        }
        HttpClient(engine).use { client ->
            val error = assertFailsWith<ApiException> { ApiClient(client, MemoryTokenStore(), baseUrl).login("테스터") }
            assertEquals(listOf(FieldErrorDto("nickname", "필수입니다.")), error.errors)
        }
    }

    @Test
    fun memoReplacementSendsFreeMemoAndDeleteAcceptsNoContent() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            when {
                request.url.encodedPath == "/api/members/me" -> respond(memberJson, headers = jsonHeaders)
                request.method == HttpMethod.Delete -> respond("", HttpStatusCode.NoContent)
                else -> {
                    assertEquals(HttpMethod.Put, request.method)
                    assertEquals("""{"freeMemo":""}""", (request.body as TextContent).text)
                    respond("""{"code":"SUCCESS","message":"저장","data":{"propertyId":9,"freeMemo":""}}""", headers = jsonHeaders)
                }
            }
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, MemoryTokenStore("saved-token"), baseUrl)
            api.restoreSession()
            assertEquals("", api.saveMemo(9, "").freeMemo)
            api.deleteProperty(9)
            assertEquals(3, requests.size)
        }
    }

    @Test
    fun pdfUsesSelectedIdsAndRejectsHtmlContent() = runTest {
        var returnPdf = true
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/members/me") respond(memberJson, headers = jsonHeaders)
            else {
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/api/properties/export.pdf", request.url.encodedPath)
                assertEquals("""{"propertyIds":[3,9]}""", (request.body as TextContent).text)
                if (returnPdf) respond("%PDF-1.7 test", headers = headersOf(HttpHeaders.ContentType, "application/pdf"))
                else respond("<html>proxy failure</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
            }
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, MemoryTokenStore("saved-token"), baseUrl)
            api.restoreSession()
            assertContentEquals("%PDF-1.7 test".encodeToByteArray(), api.comparisonPdf(listOf(3, 9)))
            returnPdf = false
            assertEquals("INVALID_RESPONSE", assertFailsWith<ApiException> { api.comparisonPdf(listOf(3, 9)) }.code)
        }
    }

    @Test
    fun comparisonAndPhotoLimitsFailBeforeNetworkMutation() = runTest {
        val engine = MockEngine { error("유효하지 않은 요청은 전송하지 않습니다.") }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, MemoryTokenStore(), baseUrl)
            assertEquals("CLIENT_VALIDATION", assertFailsWith<ApiException> { api.comparisonPdf(listOf(3, 3)) }.code)
            assertEquals("CLIENT_VALIDATION", assertFailsWith<ApiException> { api.uploadPhoto(3, byteArrayOf(), "photo.jpg", "image/jpeg") }.code)
        }
    }

    @Test
    fun nearbyEncodesCoordinatesAndCategoriesWithoutGuessingPlaces() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath == "/api/members/me") respond(memberJson, headers = jsonHeaders)
            else {
                assertEquals("500", request.url.parameters["radius"])
                assertEquals("HOSPITAL,CONVENIENCE", request.url.parameters["categories"])
                respond("""{"code":"SUCCESS","message":"ok","data":{"center":{"latitude":37.5,"longitude":127.0},"radius":500,"counts":{"HOSPITAL":0,"CONVENIENCE":0},"places":[]}}""", headers = jsonHeaders)
            }
        }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, MemoryTokenStore("saved-token"), baseUrl)
            api.restoreSession()
            assertTrue(api.nearby(37.5, 127.0, 500, listOf(MapCategory.HOSPITAL, MapCategory.CONVENIENCE)).places.isEmpty())
        }
    }

    @Test
    fun logoutClearsTokenAndPreventsFurtherAuthenticatedRequests() = runTest {
        val store = MemoryTokenStore("saved-token")
        val engine = MockEngine { respond(memberJson, headers = jsonHeaders) }
        HttpClient(engine).use { client ->
            val api = ApiClient(client, store, baseUrl)
            api.restoreSession()
            api.logout()
            assertNull(store.token)
            assertIs<AuthSession.LoggedOut>(api.session.value)
            assertEquals("AUTH_REQUIRED", assertFailsWith<ApiException> { api.properties() }.code)
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
        const val loginJson = """{"code":"SUCCESS","message":"ok","data":{"accessToken":"test-token","tokenType":"Bearer","expiresIn":43200,"newMember":false,"member":{"memberId":7,"name":"테스터","passwordProtected":true}}}"""
        const val memberJson = """{"code":"SUCCESS","message":"ok","data":{"id":7,"name":"테스터","passwordProtected":true}}"""
        const val emptyProperties = """{"code":"SUCCESS","message":"ok","data":{"totalCount":0,"items":[]}}"""
    }
}

internal class MemoryTokenStore(var token: String? = null) : TokenStore {
    override suspend fun read(): String? = token
    override suspend fun write(token: String) { this.token = token }
    override suspend fun clear() { token = null }
}
