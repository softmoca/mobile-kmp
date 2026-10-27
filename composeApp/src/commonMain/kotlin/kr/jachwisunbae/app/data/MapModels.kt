package kr.jachwisunbae.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class MapCategory { HOSPITAL, SCHOOL, CONVENIENCE, TRANSPORT, AGENCY }

@Serializable
data class CoordinateDto(val latitude: Double, val longitude: Double)

@Serializable
data class AddressDto(
    val roadAddress: String? = null,
    val jibunAddress: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    val address: String get() = roadAddress ?: jibunAddress.orEmpty()
}

@Serializable
data class NearbyPlaceDto(
    val providerPlaceId: String,
    val name: String,
    val category: MapCategory,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
)

@Serializable
data class NearbyDto(
    val center: CoordinateDto,
    val radius: Int,
    val counts: Map<MapCategory, Int>,
    val places: List<NearbyPlaceDto>,
)

@Serializable
data class BusStopDto(
    val nodeId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int,
)

@Serializable
data class BusStopsDto(
    val center: CoordinateDto,
    val radius: Int,
    val count: Int,
    val busStops: List<BusStopDto>,
)
