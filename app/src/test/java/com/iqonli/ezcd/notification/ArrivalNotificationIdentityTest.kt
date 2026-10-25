package com.iqonli.ezcd.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArrivalNotificationIdentityTest {
    @Test
    fun collidingStringHashesStillProduceDistinctNotificationTags() {
        val first = "Aa"
        val second = "BB"

        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(
            ArrivalNotificationIdentity.tag(first),
            ArrivalNotificationIdentity.tag(second),
        )
    }
}
