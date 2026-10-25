package com.iqonli.ezcd.widget
import com.iqonli.ezcd.countdown.CountdownTimeSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class WidgetMidnightRefreshTest {
    @Test fun midnightTargetsOneSecondAfterTheNextCalendarBoundary() {
        val now=ZonedDateTime.of(2026,10,24,23,59,0,0,ZoneId.of("Asia/Shanghai"))
        assertEquals(now.plusMinutes(1).plusSeconds(1).toInstant().toEpochMilli(),WidgetMidnightRefresh.nextMillis(CountdownTimeSnapshot(now)))
    }
    @Test fun boundaryUsesCalendarDaysAcrossDaylightSavingChanges() {
        for(date in listOf(LocalDate.of(2026,3,29),LocalDate.of(2026,10,25))) {
            val now=date.atStartOfDay(ZoneId.of("Europe/Berlin"))
            assertEquals(date.plusDays(1).atStartOfDay(now.zone).plusSeconds(1).toInstant().toEpochMilli(),WidgetMidnightRefresh.nextMillis(CountdownTimeSnapshot(now)))
            assertNotEquals(now.plusHours(24).plusSeconds(1).toInstant().toEpochMilli(),WidgetMidnightRefresh.nextMillis(CountdownTimeSnapshot(now)))
        }
    }
}
