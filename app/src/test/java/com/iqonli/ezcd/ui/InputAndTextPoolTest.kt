package com.iqonli.ezcd.ui
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class InputAndTextPoolTest {
    @Test fun compactTimeAcceptsOnlyFourDigitsInThe24HourRange() {
        assertEquals(0,CompactTimeInput.parse("0000"));assertEquals(540,CompactTimeInput.parse("0900"));assertEquals(1439,CompactTimeInput.parse("2359"))
        for(raw in listOf("900","09000","2400","1260","-100","09:00"," 0900","０９００")) assertNull(raw,CompactTimeInput.parse(raw))
    }
    @Test fun dateTokensKeepSurroundingTextAndDoNotDependOnLocale() {
        assertEquals("2026年01月02日",TextPool.expandDate("%yyyy%年%mm%月%dd%日",LocalDate.of(2026,1,2)))
        assertEquals("02/01/2026 %unknown%",TextPool.expandDate("%dd%/%mm%/%yyyy% %unknown%",LocalDate.of(2026,1,2)))
    }
}
