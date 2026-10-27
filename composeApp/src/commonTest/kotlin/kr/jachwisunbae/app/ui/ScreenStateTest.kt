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
import kr.jachwisunbae.app.data.AppliedChecklistDto
import kr.jachwisunbae.app.data.AppliedChecklistItemDto
import kr.jachwisunbae.app.data.ChecklistOverviewDto
import kr.jachwisunbae.app.data.CheckMemoItemDto
import kr.jachwisunbae.app.data.CheckStage
import kr.jachwisunbae.app.data.CheckStatus
import kr.jachwisunbae.app.data.CheckStatusItemDto
import kr.jachwisunbae.app.data.FieldErrorDto
import kr.jachwisunbae.app.data.MapCategory
import kr.jachwisunbae.app.data.ProgressDto
import kr.jachwisunbae.app.data.PropertySummaryDto
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

    @Test fun sessionRecoveryFailureKeepsTheServerExplanation() {
        val failure = ApiException(401, "REFRESH_TOKEN_INVALID", "이전 기록을 이어서 불러올 수 없습니다.")
        assertEquals("이전 기록을 이어서 불러올 수 없습니다.", failure.userMessage())
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

    @Test fun refreshFailureKeepsLastLoadedContentAvailableToTheScreen() = runTest {
        var requests = 0
        val state = LoadState(this) {
            if (++requests == 1) listOf("기록") else throw ApiException(0, "NETWORK_ERROR", "연결 실패")
        }
        state.reload(); runCurrent()
        state.reload()
        assertEquals(listOf("기록"), state.value)
        assertTrue(state.loading)
        runCurrent()
        assertEquals(listOf("기록"), state.value)
        assertEquals("연결 실패", state.error)
    }

    @Test fun confirmedMemoSaveCannotBeOverwrittenByAnEarlierRead() = runTest {
        val pending = CompletableDeferred<String>()
        val state = LoadState(this) { pending.await() }
        state.reload(); runCurrent()
        state.replace("서버가 저장한 메모")
        pending.complete("저장 전 메모"); runCurrent()
        assertEquals("서버가 저장한 메모", state.value)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test fun confirmedChecklistWritesRemainVisibleWhenProgressRefreshFails() = runTest {
        val original = ChecklistContent(ChecklistOverviewDto(1, ProgressDto(), emptyList()),
            AppliedChecklistDto(2, 1, checklistName = "이전 목록", stage = CheckStage.ON_SITE, items = emptyList()))
        val replacement = AppliedChecklistDto(3, 1, checklistName = "새 목록", stage = CheckStage.ON_SITE,
            items = listOf(AppliedChecklistItemDto(4, question = "채광이 좋은가요?", displayOrder = 1, status = CheckStatus.UNCONFIRMED, memo = "")))
        val confirmed = original.withSavedChecklist(replacement)
            .withSavedStatus(CheckStatusItemDto(4, CheckStatus.GOOD))
            .withSavedMemo(CheckMemoItemDto(4, "오후에도 밝아요"))
        var refreshed = false
        val state = LoadState(this) {
            if (!refreshed) throw ApiException(0, "NETWORK_ERROR", "집계 조회 실패")
            confirmed.copy(progressCurrent = true)
        }
        state.replace(confirmed)
        state.reload(); runCurrent()
        assertEquals(3L, state.value?.applied?.id)
        assertEquals(CheckStatus.GOOD, state.value?.applied?.items?.single()?.status)
        assertEquals("오후에도 밝아요", state.value?.applied?.items?.single()?.memo)
        assertFalse(state.value!!.progressCurrent)
        assertEquals("집계 조회 실패", state.error)
        refreshed = true
        state.reload(); runCurrent()
        assertTrue(state.value!!.progressCurrent)
        assertNull(state.error)
    }

    @Test fun completionFilterDoesNotTreatAnEmptyChecklistAsComplete() {
        val empty = PropertySummaryDto(id = 1, name = "창문이 있는 방", depositAmount = 0, monthlyRentAmount = 0,
            photoCount = 0, overallProgress = ProgressDto(), stages = emptyList())
        assertTrue(matchesRecord(empty, "  창문  ", RecordFilter.TO_CHECK))
        assertFalse(matchesRecord(empty, "", RecordFilter.COMPLETED))
        assertEquals("아직 체크 항목이 없어요", progressLabel(empty.overallProgress))
        val complete = empty.copy(address = "서울 성동구", overallProgress = ProgressDto(totalCount = 10, completedCount = 10, goodCount = 8, cautionCount = 2, progressRate = 100))
        assertTrue(matchesRecord(complete, "성동구", RecordFilter.COMPLETED))
        assertFalse(matchesRecord(complete, "성동구", RecordFilter.TO_CHECK))
        assertFalse(matchesRecord(complete, "강남구", RecordFilter.ALL))
        assertEquals("확인 완료 · 10개 항목", progressLabel(complete.overallProgress))
    }
}
