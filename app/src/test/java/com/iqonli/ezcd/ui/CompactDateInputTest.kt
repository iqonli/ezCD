package com.iqonli.ezcd.ui

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CompactDateInputTest {
    @Test fun eightDigitDateRoundTripsWithoutLocaleSeparators() {
        val value = LocalDate.of(2000, 10, 24)
        assertEquals(value, CompactDateInput.parse("20001024"))
        assertEquals("20001024", CompactDateInput.format(value))
    }
    @Test fun invalidCalendarDatesAndWrongFormatsAreRejected() {
        for (text in listOf("20260229", "20260431", "20261301", "20260001", "20260100", "2026-01-01", "202611", "２０２６０１０１", " 20260101")) {
            assertNull(text, CompactDateInput.parse(text))
        }
        assertEquals(LocalDate.of(2000, 2, 29), CompactDateInput.parse("20000229"))
    }
    @Test fun inputRespectsTheSameDateBoundsAsTheNativePicker() {
        assertNotNull(CompactDateInput.parse("19000101")); assertNotNull(CompactDateInput.parse("21001231"))
        assertNull(CompactDateInput.parse("18991231")); assertNull(CompactDateInput.parse("21010101"))
    }
}
