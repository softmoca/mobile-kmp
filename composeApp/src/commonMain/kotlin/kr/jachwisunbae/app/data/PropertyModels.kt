package kr.jachwisunbae.app.data

import kotlinx.serialization.Serializable

@Serializable
data class PropertyInput(
    val name: String,
    val depositAmount: Long,
    val monthlyRentAmount: Long,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val availableMoveInDate: String? = null,
    val maintenanceFeeAmount: Long? = null,
    val visitScheduledAt: String? = null,
    val discoverySource: String? = null,
    val roomOptions: List<String> = emptyList(),
    val utilityOptions: List<String> = emptyList(),
)

@Serializable
data class ProgressDto(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val goodCount: Int = 0,
    val cautionCount: Int = 0,
    val unconfirmedCount: Int = 0,
    val progressRate: Int = 0,
)

@Serializable
data class RepresentativePhotoDto(val id: Long, val url: String, val contentType: String)

@Serializable
data class PropertySummaryDto(
    val id: Long,
    val name: String,
    val depositAmount: Long,
    val monthlyRentAmount: Long,
    val discoverySource: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val photoCount: Int,
    val representativePhoto: RepresentativePhotoDto? = null,
    val overallProgress: ProgressDto,
    val stages: List<ChecklistStageDto>,
)

@Serializable
data class PropertyListDto(val totalCount: Int, val items: List<PropertySummaryDto>)

/** 생성 응답에는 photoCount, representativePhoto가 없고 photos는 빈 배열이다. */
@Serializable
data class PropertyDetailDto(
    val id: Long,
    val name: String,
    val depositAmount: Long,
    val monthlyRentAmount: Long,
    val discoverySource: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val availableMoveInDate: String? = null,
    val maintenanceFeeAmount: Long? = null,
    val visitScheduledAt: String? = null,
    val roomOptions: List<String>,
    val utilityOptions: List<String>,
    val photoCount: Int = 0,
    val photos: List<PhotoDto>,
    val representativePhoto: RepresentativePhotoDto? = null,
    val overallProgress: ProgressDto,
    val createdAt: String,
    val updatedAt: String,
) {
    fun toInput() = PropertyInput(
        name, depositAmount, monthlyRentAmount, address, latitude, longitude, availableMoveInDate,
        maintenanceFeeAmount, visitScheduledAt, discoverySource, roomOptions, utilityOptions,
    )
}

@Serializable
data class PropertyUpdateDto(
    val id: Long,
    val name: String,
    val depositAmount: Long,
    val monthlyRentAmount: Long,
    val discoverySource: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val availableMoveInDate: String? = null,
    val maintenanceFeeAmount: Long? = null,
    val visitScheduledAt: String? = null,
    val roomOptions: List<String>,
    val utilityOptions: List<String>,
    val updatedAt: String,
)

@Serializable
data class PropertyMemoDto(val propertyId: Long, val freeMemo: String)

@Serializable
internal data class MemoRequest(val freeMemo: String)

@Serializable
data class PhotoDto(
    val id: Long,
    val propertyId: Long? = null,
    val url: String,
    val contentType: String,
    val sizeBytes: Long,
    val representative: Boolean,
    val createdAt: String,
)

@Serializable
data class PhotoListDto(val propertyId: Long, val totalCount: Int, val items: List<PhotoDto>)

@Serializable
internal data class ComparisonRequest(val propertyIds: List<Long>)
