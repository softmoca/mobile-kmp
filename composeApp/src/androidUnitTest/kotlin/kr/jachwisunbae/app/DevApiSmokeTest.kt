package kr.jachwisunbae.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kr.jachwisunbae.app.data.*
import org.junit.Assume.assumeTrue

/** 명시적으로 켠 경우에만 새로운 익명 개발 회원을 만든다. 기존 회원/운영 서버는 사용하지 않는다. */
class DevApiSmokeTest {
    private class MemoryStore : TokenStore {
        var value: String? = null
        override suspend fun read() = value
        override suspend fun write(token: String) { value = token }
        override suspend fun clear() { value = null }
    }

    @Test
    fun disposableDevelopmentAccountExercisesRealClient() = runBlocking<Unit> {
        assumeTrue("RUN_DEV_API_SMOKE=1 opt-in required", System.getenv("RUN_DEV_API_SMOKE") == "1")
        val client = HttpClient(OkHttp) {
            followRedirects = false
            install(HttpTimeout) { requestTimeoutMillis = 90_000; connectTimeoutMillis = 15_000 }
        }
        val rejectNextAccess = AtomicBoolean(false)
        val refreshRequests = AtomicInteger(0)
        // 실제 서버에 딱 한 번 무효 Bearer를 보내 401 → refresh → 재요청 경로를 검증한다.
        // 앱 코드/서버를 바꾸지 않으며 토큰이나 응답 본문을 로그에 남기지 않는다.
        client.plugin(HttpSend).intercept { request ->
            if (request.url.build().encodedPath == "/api/auth/refresh") refreshRequests.incrementAndGet()
            if (request.url.build().encodedPath == "/api/properties" && rejectNextAccess.compareAndSet(true, false)) {
                request.headers.remove(HttpHeaders.Authorization)
                request.headers.append(HttpHeaders.Authorization, "Bearer deliberately-invalid-smoke-access")
            }
            execute(request)
        }
        val store = MemoryStore()
        var api = ApiClient(client, store, DEV_API)
        val propertyIds = mutableListOf<Long>()
        var checklistId: Long? = null
        val runId = UUID.randomUUID().toString().take(8)
        var testFailure: Throwable? = null
        try {
            val login = api.login()
            assertTrue(login.newMember)
            val memberId = login.member.memberId
            assertEquals(memberId, api.member().id)
            assertNotNull(store.value)
            assertFalse(store.value!!.contains(login.accessToken), "Access Token must remain memory-only")
            assertEquals(0, api.properties().totalCount)

            // 새 클라이언트에는 메모리 Access Token이 없으므로 보관한 Refresh Cookie로만 복원한다.
            api = ApiClient(client, store, DEV_API)
            api.restoreSession()
            assertEquals(memberId, assertIs<AuthSession.Authenticated>(api.session.value).member.id)
            assertEquals(memberId, api.refresh().member.memberId)
            val before401 = refreshRequests.get()
            rejectNextAccess.set(true)
            assertEquals(0, api.properties().totalCount)
            assertEquals(before401 + 1, refreshRequests.get(), "A real 401 must cause exactly one refresh")
            assertEquals(memberId, api.member().id)

            val items = api.systemCheckItems(CheckStage.ON_SITE)
            assertTrue(items.size >= 3)
            val template = api.createUserChecklist("KMP QA $runId", CheckStage.ON_SITE, items.take(2).map { it.id })
            checklistId = template.id
            assertEquals(2, api.userChecklist(template.id).itemCount)
            val updatedTemplateName = "KMP QA $runId 수정"
            assertEquals(updatedTemplateName, api.updateUserChecklist(template.id, updatedTemplateName, items.take(3).map { it.id }).name)
            repeat(2) { index ->
                val p = api.createProperty(PropertyInput("KMP QA $runId 매물$index", 5_000_000, 500_000,
                    maintenanceFeeAmount = 50_000))
                propertyIds += p.id
                assertEquals(0, api.photos(p.id).totalCount)
            }
            val id = propertyIds.first()
            val initial = api.property(id)
            val updatedName = "KMP QA $runId 수정매물"
            api.updateProperty(id, initial.toInput().copy(name = updatedName))
            assertEquals(updatedName, api.property(id).name)
            api.saveMemo(id, "KMP QA $runId 자동화 검증용 메모\n긴 메모의 줄바꿈 확인")
            assertTrue(api.memo(id).freeMemo.contains(runId))
            val applied = api.applyChecklist(id, CheckStage.ON_SITE, ChecklistSource.USER, template.id)
            val item = applied.items.first()
            api.updateCheckStatus(id, applied.id, item.id, CheckStatus.GOOD)
            api.updateCheckMemo(id, applied.id, item.id, "KMP QA $runId 항목 메모")
            val refreshed = api.appliedChecklist(id, applied.id)
            assertEquals(CheckStatus.GOOD, refreshed.items.first().status)
            assertEquals("KMP QA $runId 항목 메모", refreshed.items.first().memo)
            assertTrue(api.checklistOverview(id).overallProgress.completedCount > 0)
            api.updateCheckStatus(id, applied.id, item.id, CheckStatus.CAUTION)
            assertEquals(CheckStatus.CAUTION, api.appliedChecklist(id, applied.id).items.first().status)
            api.updateCheckStatus(id, applied.id, item.id, CheckStatus.UNCONFIRMED)
            assertEquals(CheckStatus.UNCONFIRMED, api.appliedChecklist(id, applied.id).items.first().status)
            api.applyChecklist(id, CheckStage.PRE_CONTRACT, ChecklistSource.SYSTEM_DEFAULT)
            val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
            val photo = api.uploadPhoto(id, png, "kmp-qa-$runId.png", "image/png")
            assertTrue(api.photos(id).items.any { it.id == photo.id })
            assertTrue(api.photoBytes(id, photo.id).isNotEmpty())
            api.setRepresentativePhoto(id, photo.id)
            assertEquals(photo.id, api.property(id).representativePhoto?.id)
            api.deletePhoto(id, photo.id)
            assertEquals(0, api.photos(id).totalCount)
            val pdf = api.comparisonPdf(propertyIds)
            assertEquals("%PDF-", pdf.take(5).toByteArray().decodeToString())
            println("DEV_SMOKE: run=$runId member=$memberId auth/refresh/real401/restore/propertyCRUD/memo/templateCRUD/checkStatus/photoCRUD/PDF passed; PDF ${pdf.size} bytes")
            api.deleteUserChecklist(template.id)
            checklistId = null
            propertyIds.toList().forEach { api.deleteProperty(it); propertyIds.remove(it) }
            assertEquals(0, api.properties().totalCount)
            assertEquals(0, api.userChecklists().totalCount)
            println("DEV_SMOKE_CLEANUP: run=$runId properties=0 checklists=0")
        } catch (failure: Throwable) {
            testFailure = failure
            throw failure
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            for (id in propertyIds) runCatching { api.deleteProperty(id) }.onFailure {
                println("CLEANUP_FAILED property=$id run=$runId")
                cleanupFailures += it
            }
            checklistId?.let { id -> runCatching { api.deleteUserChecklist(id) }.onFailure {
                println("CLEANUP_FAILED checklist=$id run=$runId")
                cleanupFailures += it
            } }
            client.close()
            cleanupFailures.forEach { testFailure?.addSuppressed(it) }
            if (testFailure == null && cleanupFailures.isNotEmpty()) throw AssertionError("Development test cleanup failed", cleanupFailures.first())
        }
    }

    @Test
    fun realDevelopmentMapProviders() = runBlocking<Unit> {
        assumeTrue("RUN_DEV_API_SMOKE=1 opt-in required", System.getenv("RUN_DEV_API_SMOKE") == "1")
        val client = HttpClient(OkHttp) { followRedirects = false; install(HttpTimeout) { requestTimeoutMillis = 60_000 } }
        val api = ApiClient(client, MemoryStore(), DEV_API)
        try {
            api.login()
            val places = api.geocode("서울특별시 중구 세종대로 110")
            assertTrue(places.isNotEmpty())
            val place = places.first()
            assertTrue(place.latitude in 33.0..39.0)
            val address = api.reverseGeocode(place.latitude, place.longitude)
            assertTrue(address.address.isNotEmpty())
            val nearby = api.nearby(place.latitude, place.longitude)
            assertEquals(500, nearby.radius)
            assertTrue(nearby.places.isNotEmpty())
            val filtered = api.nearby(place.latitude, place.longitude, categories = listOf(MapCategory.CONVENIENCE))
            assertTrue(filtered.places.isNotEmpty())
            assertTrue(filtered.places.all { it.category == MapCategory.CONVENIENCE })
            assertEquals(nearby.counts[MapCategory.TRANSPORT], nearby.places.count { it.category == MapCategory.TRANSPORT })
            println("DEV_MAP: address search/reverse/nearby/category-filter passed; places=${nearby.places.size}; filtered=${filtered.places.size}; busStops=${nearby.counts[MapCategory.TRANSPORT]}")
        } finally { client.close() }
    }

    private companion object { const val DEV_API = "https://dev-api.jachwi-sunbae.kr" }
}
