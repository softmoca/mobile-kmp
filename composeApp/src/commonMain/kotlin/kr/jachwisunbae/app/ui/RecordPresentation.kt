package kr.jachwisunbae.app.ui

import kr.jachwisunbae.app.data.PropertySummaryDto

internal enum class RecordFilter(val label: String) {
    ALL("전체"), TO_CHECK("확인할 집"), COMPLETED("확인 완료")
}

internal fun matchesRecord(property: PropertySummaryDto, query: String, filter: RecordFilter): Boolean {
    val search = query.trim()
    val matchesQuery = search.isEmpty() || property.name.contains(search, ignoreCase = true) || property.address.orEmpty().contains(search, ignoreCase = true)
    val progress = property.overallProgress
    val completed = progress.totalCount > 0 && progress.completedCount >= progress.totalCount
    return matchesQuery && when (filter) {
        RecordFilter.ALL -> true
        RecordFilter.TO_CHECK -> !completed
        RecordFilter.COMPLETED -> completed
    }
}
