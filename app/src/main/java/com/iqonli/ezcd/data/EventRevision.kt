// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.data

import com.iqonli.ezcd.model.CountdownEvent
import java.nio.ByteBuffer
import java.security.MessageDigest

/** A bounded comparison token; never replaces event data in storage or backups. */
object EventRevision {
    fun of(event: CountdownEvent): String = ofFields(
        event.id, event.title, event.date.toString(), event.type.name, event.icon.name,
        event.reminder.name, event.createdAt.toString(), event.repeatRule.name, event.countMode.name, event.includeToday.toString(), event.cardColor.toString(), event.countColor.toString(), event.reminderMinuteOfDay.toString(), event.cardColorCustom.toString(),event.countColorCustom.toString(),event.cardColorDefault.toString(), event.countColorDefault.toString(),
    )

    fun ofFields(vararg fields: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
