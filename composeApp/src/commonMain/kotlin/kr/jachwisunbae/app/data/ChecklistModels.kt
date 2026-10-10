package kr.jachwisunbae.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class CheckStage { ON_SITE, PRE_CONTRACT }

@Serializable
enum class CheckStatus { UNCONFIRMED, GOOD, CAUTION }

@Serializable
enum class ChecklistSource { USER, SYSTEM_DEFAULT }

@Serializable
data class SystemCheckItemDto(val id: Long, val stage: CheckStage, val itemType: String, val question: String)

@Serializable
data class UserChecklistSummaryDto(val id: Long, val name: String, val stage: CheckStage, val itemCount: Int)

@Serializable
data class UserChecklistListDto(val totalCount: Int, val items: List<UserChecklistSummaryDto>)

@Serializable
data class UserChecklistItemDto(
    val id: Long,
    val systemCheckItemId: Long? = null,
    val itemType: String,
    val question: String,
    val displayOrder: Int,
    val active: Boolean,
)

@Serializable
data class UserChecklistDto(
    val id: Long,
    val name: String,
    val stage: CheckStage,
    val itemCount: Int,
    val items: List<UserChecklistItemDto>,
)

@Serializable
internal data class UserChecklistItemInput(val systemCheckItemId: Long)

@Serializable
internal data class CreateUserChecklistRequest(
    val name: String,
    val stage: CheckStage,
    val items: List<UserChecklistItemInput>,
)

@Serializable
internal data class UpdateUserChecklistRequest(val name: String, val items: List<UserChecklistItemInput>)

@Serializable
data class ChecklistStageDto(
    val stage: CheckStage,
    val applied: Boolean,
    val propertyChecklistId: Long? = null,
    val checklistName: String? = null,
    val sourceChecklistId: Long? = null,
    val progress: ProgressDto,
)

@Serializable
data class ChecklistOverviewDto(
    val propertyId: Long,
    val overallProgress: ProgressDto,
    val stages: List<ChecklistStageDto>,
)

@Serializable
data class AppliedChecklistItemDto(
    val id: Long,
    val systemCheckItemId: Long? = null,
    val question: String,
    val displayOrder: Int,
    val status: CheckStatus,
    val memo: String,
)

@Serializable
data class AppliedChecklistDto(
    val id: Long,
    val propertyId: Long,
    val sourceChecklistId: Long? = null,
    val checklistName: String,
    val stage: CheckStage,
    val items: List<AppliedChecklistItemDto>,
)

@Serializable
internal data class ApplyChecklistRequest(val sourceType: ChecklistSource, val checklistId: Long?)

@Serializable
internal data class CheckStatusRequest(val status: CheckStatus)

@Serializable
internal data class CheckMemoRequest(val memo: String)

@Serializable
data class CheckStatusItemDto(val id: Long, val status: CheckStatus)

@Serializable
data class CheckStatusDto(val item: CheckStatusItemDto)

@Serializable
data class CheckMemoItemDto(val id: Long, val memo: String)

@Serializable
data class CheckMemoDto(val item: CheckMemoItemDto)
