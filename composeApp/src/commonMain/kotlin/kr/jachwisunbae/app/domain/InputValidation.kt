package kr.jachwisunbae.app.domain

import kr.jachwisunbae.app.data.ApiException
import kr.jachwisunbae.app.data.FieldErrorDto
import kr.jachwisunbae.app.data.PropertyInput

/** 백엔드 Controller 제약과 도메인 검증을 보조한다. 최종 판정은 서버가 담당한다. */
object InputValidation {
    val roomOptions = setOf(
        "AIR_CONDITIONER", "REFRIGERATOR", "WASHING_MACHINE", "SINK", "GAS_STOVE", "MICROWAVE",
        "SHOE_CABINET", "WARDROBE", "BED", "DESK", "TV", "INDUCTION",
    )
    val utilityOptions = setOf("WATER", "ELECTRICITY", "GAS", "INTERNET")

    fun property(input: PropertyInput): List<FieldErrorDto> = buildList {
        if (input.name.isBlank() || input.name.length > 30) add(FieldErrorDto("name", "매물 이름은 1~30자로 입력해 주세요."))
        if (input.depositAmount < 0) add(FieldErrorDto("depositAmount", "보증금은 0원 이상이어야 합니다."))
        if (input.monthlyRentAmount < 0) add(FieldErrorDto("monthlyRentAmount", "월세는 0원 이상이어야 합니다."))
        if (input.maintenanceFeeAmount != null && input.maintenanceFeeAmount < 0) {
            add(FieldErrorDto("maintenanceFeeAmount", "관리비는 0원 이상이어야 합니다."))
        }
        if ((input.address?.length ?: 0) > 255) add(FieldErrorDto("address", "주소는 255자 이내로 입력해 주세요."))
        if ((input.discoverySource?.length ?: 0) > 500) add(FieldErrorDto("discoverySource", "발견 경로는 500자 이내로 입력해 주세요."))
        if ((input.latitude == null) != (input.longitude == null)) {
            add(FieldErrorDto("latitude", "위도와 경도를 함께 선택해 주세요."))
        }
        if (input.latitude != null && (!input.latitude.isFinite() || input.latitude !in -90.0..90.0)) {
            add(FieldErrorDto("latitude", "올바른 위도를 선택해 주세요."))
        }
        if (input.longitude != null && (!input.longitude.isFinite() || input.longitude !in -180.0..180.0)) {
            add(FieldErrorDto("longitude", "올바른 경도를 선택해 주세요."))
        }
        if (input.availableMoveInDate != null && !isDate(input.availableMoveInDate)) {
            add(FieldErrorDto("availableMoveInDate", "입주 가능일을 YYYY-MM-DD 형식으로 입력해 주세요."))
        }
        if (input.visitScheduledAt != null && !isLocalDateTime(input.visitScheduledAt)) {
            add(FieldErrorDto("visitScheduledAt", "방문 일시를 YYYY-MM-DDTHH:mm 형식으로 입력해 주세요."))
        }
        if (input.roomOptions.any { it !in roomOptions }) add(FieldErrorDto("roomOptions", "지원하는 방 옵션을 선택해 주세요."))
        if (input.utilityOptions.any { it !in utilityOptions }) add(FieldErrorDto("utilityOptions", "지원하는 공과금 항목을 선택해 주세요."))
    }

    fun checklist(name: String, systemItemIds: List<Long>): List<FieldErrorDto> = buildList {
        if (name.trim().length !in 1..30) add(FieldErrorDto("name", "체크리스트 이름은 1~30자로 입력해 주세요."))
        if (systemItemIds.size !in 1..30) add(FieldErrorDto("items", "체크 항목을 1~30개 선택해 주세요."))
        if (systemItemIds.any { it <= 0 } || systemItemIds.distinct().size != systemItemIds.size) {
            add(FieldErrorDto("items", "중복 없이 제공된 체크 항목을 선택해 주세요."))
        }
    }

    fun comparison(propertyIds: List<Long>): List<FieldErrorDto> = buildList {
        if (propertyIds.size !in 2..5 || propertyIds.any { it <= 0 } || propertyIds.distinct().size != propertyIds.size) {
            add(FieldErrorDto("propertyIds", "서로 다른 매물을 2~5개 선택해 주세요."))
        }
    }

    fun requireValid(errors: List<FieldErrorDto>) {
        if (errors.isNotEmpty()) throw ApiException(400, "CLIENT_VALIDATION", errors.first().reason, errors)
    }

    fun isDate(value: String): Boolean {
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return false
        val year = value.substring(0, 4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        if (month !in 1..12) return false
        val leap = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
        val days = when (month) { 2 -> if (leap) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
        return day in 1..days
    }

    fun isLocalDateTime(value: String): Boolean {
        val parts = value.split('T')
        if (parts.size != 2 || !isDate(parts[0])) return false
        val time = Regex("(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.\\d{1,9})?)?").matchEntire(parts[1]) ?: return false
        return time.groupValues[1].toInt() in 0..23 && time.groupValues[2].toInt() in 0..59 &&
            (time.groupValues[3].isEmpty() || time.groupValues[3].toInt() in 0..59)
    }
}

/** 비교 선택은 순서를 보존하며 최대 5개를 넘지 않는다. */
data class ComparisonSelection(val ids: List<Long> = emptyList()) {
    init { require(ids.size <= 5 && ids.all { it > 0 } && ids.distinct().size == ids.size) }
    val canCompare: Boolean get() = ids.size in 2..5
    fun toggle(id: Long): ComparisonSelection {
        require(id > 0)
        if (id in ids) return copy(ids = ids - id)
        if (ids.size == 5) throw ApiException(400, "COMPARISON_LIMIT", "최대 5개까지 비교할 수 있습니다.")
        return copy(ids = ids + id)
    }
}
