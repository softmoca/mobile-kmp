package kr.jachwisunbae.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kr.jachwisunbae.app.data.*
import org.junit.Assume.assumeTrue
import java.util.UUID

/** 명시적으로 켠 경우에만 새 개발 테스트 회원에서 동작한다. 기존 회원/운영 서버는 사용하지 않는다. */
class DevApiSmokeTest {
    @Test
    fun disposableDevelopmentAccountExercisesRealClient() = runBlocking<Unit> {
        assumeTrue("RUN_DEV_API_SMOKE=1 opt-in required", System.getenv("RUN_DEV_API_SMOKE") == "1")
        val client = HttpClient(OkHttp) {
            followRedirects = false
            install(HttpTimeout) { requestTimeoutMillis = 90_000; connectTimeoutMillis = 15_000 }
        }
        val store = object : TokenStore {
            var token: String? = null
            override suspend fun read() = token
            override suspend fun write(token: String) { this.token = token }
            override suspend fun clear() { token = null }
        }
        val api = ApiClient(client, store, "https://dev-api.jachwi-sunbae.kr")
        val propertyIds = mutableListOf<Long>()
        var checklistId: Long? = null
        try {
            val nickname = "kmp-qa-" + UUID.randomUUID().toString().take(18)
            val password = UUID.randomUUID().toString().take(20)
            assertTrue(api.login(nickname, password).newMember)
            assertEquals(0, api.properties().totalCount)
            api.restoreSession()
            assertIs<AuthSession.Authenticated>(api.session.value)
            val items = api.systemCheckItems(CheckStage.ON_SITE)
            assertTrue(items.isNotEmpty())
            val template = api.createUserChecklist("KMP 자동검증", CheckStage.ON_SITE, items.take(2).map { it.id })
            checklistId = template.id
            assertEquals(2, api.userChecklist(template.id).itemCount)
            assertEquals("KMP 검증 수정", api.updateUserChecklist(template.id, "KMP 검증 수정", items.take(3).map { it.id }).name)
            repeat(2) { index ->
                val p = api.createProperty(PropertyInput("KMP 검증 매물 $index", 5_000_000, 500_000,
                    maintenanceFeeAmount = 50_000))
                propertyIds += p.id
            }
            val id = propertyIds.first()
            val initial = api.property(id)
            api.updateProperty(id, initial.toInput().copy(name = "KMP 검증 수정 매물"))
            assertEquals("KMP 검증 수정 매물", api.property(id).name)
            api.saveMemo(id, "자동화 검증용 메모")
            assertEquals("자동화 검증용 메모", api.memo(id).freeMemo)
            val applied = api.applyChecklist(id, CheckStage.ON_SITE, ChecklistSource.USER, template.id)
            val item = applied.items.first()
            api.updateCheckStatus(id, applied.id, item.id, CheckStatus.GOOD)
            api.updateCheckMemo(id, applied.id, item.id, "테스트 항목 메모")
            val refreshed = api.appliedChecklist(id, applied.id)
            assertEquals(CheckStatus.GOOD, refreshed.items.first().status)
            assertEquals("테스트 항목 메모", refreshed.items.first().memo)
            assertTrue(api.checklistOverview(id).overallProgress.completedCount > 0)
            api.applyChecklist(id, CheckStage.PRE_CONTRACT, ChecklistSource.SYSTEM_DEFAULT)
            val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
            val photo = api.uploadPhoto(id, png, "smoke.png", "image/png")
            assertTrue(api.photos(id).items.any { it.id == photo.id })
            assertTrue(api.photoBytes(id, photo.id).isNotEmpty())
            api.setRepresentativePhoto(id, photo.id)
            assertEquals(photo.id, api.property(id).representativePhoto?.id)
            api.deletePhoto(id, photo.id)
            assertEquals(0, api.photos(id).totalCount)
            val pdf = api.comparisonPdf(propertyIds)
            assertEquals("%PDF-", pdf.take(5).toByteArray().decodeToString())
            println("DEV_SMOKE: auth/restore/empty/propertyCRUD/memo/templateCRUD/checkStatus/photoCRUD/PDF passed; PDF ${pdf.size} bytes")
            api.deleteUserChecklist(template.id)
            checklistId = null
            propertyIds.toList().forEach { api.deleteProperty(it); propertyIds.remove(it) }
            assertEquals(0, api.properties().totalCount)
            api.logout()
            assertNull(store.token)
            assertIs<AuthSession.LoggedOut>(api.session.value)
        } finally {
            for (id in propertyIds) runCatching { api.deleteProperty(id) }.onFailure { println("CLEANUP_FAILED property $id: ${it::class.simpleName}") }
            checklistId?.let { runCatching { api.deleteUserChecklist(it) }.onFailure { println("CLEANUP_FAILED checklist $it") } }
            client.close()
        }
    }

    @Test
    fun realDevelopmentMapProviders() = runBlocking<Unit> {
        assumeTrue("RUN_DEV_API_SMOKE=1 opt-in required", System.getenv("RUN_DEV_API_SMOKE") == "1")
        val client = HttpClient(OkHttp) { followRedirects = false; install(HttpTimeout) { requestTimeoutMillis = 60_000 } }
        val store = object : TokenStore {
            override suspend fun read(): String? = null
            override suspend fun write(token: String) = Unit
            override suspend fun clear() = Unit
        }
        val api = ApiClient(client, store, "https://dev-api.jachwi-sunbae.kr")
        try {
            api.login("kmp-map-" + UUID.randomUUID().toString().take(18), UUID.randomUUID().toString().take(20))
            val places = api.geocode("서울특별시 중구 세종대로 110")
            assertTrue(places.isNotEmpty())
            val place = places.first()
            assertTrue(place.latitude in 33.0..39.0)
            val address = api.reverseGeocode(place.latitude, place.longitude)
            assertTrue(address.address.isNotEmpty())
            val nearby = api.nearby(place.latitude, place.longitude)
            assertEquals(500, nearby.radius)
            assertTrue(nearby.places.isNotEmpty())
            println("DEV_MAP: address search/reverse/nearby passed; places=${nearby.places.size}")
        } finally { client.close() }
    }
}
