package kr.jachwisunbae.app.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kr.jachwisunbae.app.data.ApiException
import kr.jachwisunbae.app.data.FieldErrorDto
import kr.jachwisunbae.app.data.MapCategory
import kr.jachwisunbae.app.platform.Coordinates

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenStateTest {
    @Test fun emptyResponseIsSuccessfulContent() = runTest {
        val state = LoadState(this) { emptyList<String>() }
        state.reload()
        runCurrent()
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(emptyList(), state.value)
    }

    @Test fun networkFailureCanBeRetriedWithoutKeepingTheError() = runTest {
        var connected = false
        val state = LoadState(this) {
            if (!connected) throw ApiException(0, "NETWORK_ERROR", "연결을 확인해 주세요.")
            listOf("저장된 집")
        }
        state.reload(); runCurrent()
        assertFalse(state.loading)
        assertEquals("연결을 확인해 주세요.", state.error)
        connected = true
        state.reload(); runCurrent()
        assertNull(state.error)
        assertEquals(listOf("저장된 집"), state.value)
    }

    @Test fun newRefreshCancelsStaleResponseAndKeepsLoadingUntilLatestCompletes() = runTest {
        val first = CompletableDeferred<String>()
        val second = CompletableDeferred<String>()
        var attempt = 0
        val state = LoadState(this) { if (++attempt == 1) first.await() else second.await() }
        state.reload(); runCurrent()
        state.reload(); runCurrent()
        assertTrue(state.loading)
        first.complete("이전 기록"); runCurrent()
        assertNull(state.value)
        assertTrue(state.loading)
        second.complete("최신 기록"); runCurrent()
        assertEquals("최신 기록", state.value)
        assertFalse(state.loading)
    }

    @Test fun repeatedSaveTapSubmitsOnlyOnceUntilCompletion() = runTest {
        val pending = CompletableDeferred<Unit>()
        var writes = 0
        val action = ActionState(this)
        action.run("저장 완료") { writes++; pending.await() }
        action.run("중복 저장") { writes++ }
        runCurrent()
        assertEquals(1, writes)
        assertTrue(action.busy)
        pending.complete(Unit); runCurrent()
        assertFalse(action.busy)
        assertEquals("저장 완료", action.success)
    }

    @Test fun leavingScreenDoesNotDisplayCancellationAsFailureOrSuccess() = runTest {
        val screenJob = Job()
        val action = ActionState(CoroutineScope(coroutineContext + screenJob))
        action.run("저장 완료") { awaitCancellation() }
        runCurrent()
        screenJob.cancel(); runCurrent()
        assertFalse(action.busy)
        assertNull(action.error)
        assertNull(action.success)
    }

    @Test fun fieldErrorsAndPermissionHelpReachTheUserWithoutDuplicateLines() {
        val exception = ApiException(400, "INVALID_INPUT", "입력값을 확인해 주세요.", listOf(
            FieldErrorDto("name", "이름을 입력해 주세요."), FieldErrorDto("name", "이름을 입력해 주세요.")))
        assertEquals("입력값을 확인해 주세요.\n이름을 입력해 주세요.", exception.userMessage())
        assertEquals("설정에서 위치 권한을 허용해 주세요.", IllegalStateException("설정에서 위치 권한을 허용해 주세요.").userMessage())
    }

    @Test fun wrongPasswordKeepsAuthenticationFailureMessageInsteadOfSessionExpiry() {
        val failure = ApiException(401, "NICKNAME_AUTHENTICATION_FAILED", "닉네임 또는 비밀번호를 확인해 주세요.")
        assertEquals("닉네임 또는 비밀번호를 확인해 주세요.", failure.userMessage())
    }

    @Test fun nearbyResponseIsDiscardedWhenLocationRadiusOrCategoryChanges() = runTest {
        val requested = NearbyQuery(Coordinates(37.5, 127.0), 500, setOf(MapCategory.HOSPITAL))
        val changedQueries = listOf(
            requested.copy(center = Coordinates(37.6, 127.0)),
            requested.copy(radius = 2000),
            requested.copy(categories = setOf(MapCategory.SCHOOL)),
        )
        changedQueries.forEach { changed ->
            var current = requested
            val pending = CompletableDeferred<String>()
            val request = async { fetchCurrentNearby(requested, current = { current }) { pending.await() } }
            runCurrent()
            current = changed
            pending.complete("이전 시설 결과")
            assertNull(request.await())
        }
        assertEquals("현재 시설 결과", fetchCurrentNearby(requested, current = { requested }) { "현재 시설 결과" })
    }

    @Test fun obsoleteNearbyFailureDoesNotOverwriteTheCurrentSelectionWithAnError() = runTest {
        val requested = NearbyQuery(Coordinates(37.5, 127.0), 500, emptySet())
        var current = requested
        val pending = CompletableDeferred<String>()
        val request = async { fetchCurrentNearby(requested, current = { current }) { pending.await() } }
        runCurrent()
        current = requested.copy(radius = 1000)
        pending.completeExceptionally(ApiException(503, "MAP_PROVIDER_FAILURE", "이전 요청 실패"))
        assertNull(request.await())
    }
}
