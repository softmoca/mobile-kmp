package kr.jachwisunbae.app.domain

import kr.jachwisunbae.app.data.ApiException
import kr.jachwisunbae.app.data.PropertyInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputValidationTest {
    @Test
    fun nicknameTrimsButPasswordRetainsSpaces() {
        assertTrue(InputValidation.login("  테스터  ", "    ").isEmpty())
        assertTrue(InputValidation.login("테스터", "").isEmpty())
        assertEquals("nickname", InputValidation.login(" ", null).single().field)
        assertEquals("password", InputValidation.login("테스터", "123").single().field)
    }

    @Test
    fun datesRejectImpossibleCalendarDatesAndTimezoneSuffixes() {
        assertTrue(InputValidation.isDate("2028-02-29"))
        assertTrue(InputValidation.isDate("2000-02-29"))
        assertFalse(InputValidation.isDate("1900-02-29"))
        assertFalse(InputValidation.isDate("2026-02-29"))
        assertFalse(InputValidation.isDate("2026-04-31"))
        assertFalse(InputValidation.isDate("2026-13-01"))
        assertTrue(InputValidation.isLocalDateTime("2026-10-10T14:30"))
        assertTrue(InputValidation.isLocalDateTime("2026-10-10T14:30:59.123"))
        assertFalse(InputValidation.isLocalDateTime("2026-10-10T24:00"))
        assertFalse(InputValidation.isLocalDateTime("2026-10-10T14:30:00Z"))
    }

    @Test
    fun propertyRejectsInvalidMoneyOrUnpairedLocation() {
        val errors = InputValidation.property(PropertyInput("방", -1, 0, latitude = 37.5))
        assertEquals(setOf("depositAmount", "latitude"), errors.map { it.field }.toSet())
        assertTrue(InputValidation.property(PropertyInput("방", 0, 0)).isEmpty())
        assertTrue(InputValidation.property(PropertyInput("방", 0, 0, latitude = Double.NaN, longitude = 127.0)).any { it.field == "latitude" })
    }

    @Test
    fun checklistRequiresUniqueSystemItemsWithinLimit() {
        assertTrue(InputValidation.checklist("현장 확인", listOf(1, 2)).isEmpty())
        assertFalse(InputValidation.checklist(" ", listOf(1)).isEmpty())
        assertFalse(InputValidation.checklist("현장 확인", listOf(1, 1)).isEmpty())
        assertFalse(InputValidation.checklist("현장 확인", (1L..31L).toList()).isEmpty())
    }

    @Test
    fun comparisonPreservesSelectionOrderAndEnforcesTwoToFiveDistinctProperties() {
        val one = ComparisonSelection().toggle(4)
        assertFalse(one.canCompare)
        val two = one.toggle(2)
        assertTrue(two.canCompare)
        assertEquals(listOf(4L, 2L), two.ids)
        assertEquals(listOf(2L), two.toggle(4).ids)
        val five = two.toggle(6).toggle(1).toggle(9)
        assertFailsWith<ApiException> { five.toggle(10) }
        assertFalse(InputValidation.comparison(listOf(1, 1)).isEmpty())
        assertTrue(InputValidation.comparison(listOf(4, 2)).isEmpty())
    }
}
