// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.data

import com.iqonli.ezcd.model.OpaqueColor
import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventIcon
import com.iqonli.ezcd.model.EventType
import com.iqonli.ezcd.model.ReminderOption
import com.iqonli.ezcd.model.RepeatRule
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDate

enum class CountdownDataProblem {
    CORRUPT,
    UNSUPPORTED_SCHEMA,
}

class CountdownDataException(
    val problem: CountdownDataProblem,
    cause: Throwable? = null,
) : Exception(cause)

object CountdownStorageSchema {
    const val LEGACY_VERSION = 1
    const val PREVIOUS_VERSION = 2
    const val NOTIFICATION_VERSION = 3
    const val REMINDER_VERSION = 4
    const val REPEAT_RULE_VERSION = 5
    const val EXPANDED_RECURRENCE_VERSION = 6
    const val COUNT_MODE_VERSION = 7
    const val INCLUDE_TODAY_VERSION = 8
    const val COLOR_TIME_VERSION = 9
    const val COUNT_COLOR_VERSION = 10
    const val DEFAULT_COLOR_VERSION = 11
    const val CURRENT_VERSION = DEFAULT_COLOR_VERSION

    fun isSupported(version: Int): Boolean = version in LEGACY_VERSION..CURRENT_VERSION

    fun problemFor(version: Int): CountdownDataProblem? = when {
        isSupported(version) -> null
        version > CURRENT_VERSION -> CountdownDataProblem.UNSUPPORTED_SCHEMA
        else -> CountdownDataProblem.CORRUPT
    }

    fun repeatRuleFor(version: Int, rawStorageKey: String?): RepeatRule? = when (version) {
        LEGACY_VERSION,
        PREVIOUS_VERSION,
        NOTIFICATION_VERSION,
        REMINDER_VERSION,
        -> RepeatRule.NONE
        REPEAT_RULE_VERSION,
        EXPANDED_RECURRENCE_VERSION,
        COUNT_MODE_VERSION,
        INCLUDE_TODAY_VERSION, COLOR_TIME_VERSION, COUNT_COLOR_VERSION, DEFAULT_COLOR_VERSION,
        -> rawStorageKey?.let(RepeatRule::fromStorageKey)
        else -> null
    }

    fun countModeFor(version: Int, rawStorageKey: String?): CountMode? = when (version) {
        LEGACY_VERSION,
        PREVIOUS_VERSION,
        NOTIFICATION_VERSION,
        REMINDER_VERSION,
        REPEAT_RULE_VERSION,
        EXPANDED_RECURRENCE_VERSION,
        -> CountMode.COUNT_DOWN
        COUNT_MODE_VERSION, INCLUDE_TODAY_VERSION, COLOR_TIME_VERSION, COUNT_COLOR_VERSION, DEFAULT_COLOR_VERSION -> rawStorageKey?.let(CountMode::fromStorageKey)
        else -> null
    }
}

object CountdownStorageCodec {
    fun readUtf8Payload(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > CountdownValidation.MAX_PAYLOAD_BYTES) {
                throw CountdownDataException(CountdownDataProblem.CORRUPT)
            }
            output.write(buffer, 0, count)
        }

        return try {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
        } catch (error: CharacterCodingException) {
            throw CountdownDataException(CountdownDataProblem.CORRUPT, error)
        }
    }

    fun decode(payload: String): List<CountdownEvent> = decodePayload(payload, enforceImportLimits = false)

    fun decodeForImport(payload: String): List<CountdownEvent> = decodePayload(payload, enforceImportLimits = true)

    private fun decodePayload(payload: String, enforceImportLimits: Boolean): List<CountdownEvent> {
        CountdownValidation.validatePayloadSize(payload)

        try {
            val root = JSONObject(payload)
            if (!root.has(KEY_SCHEMA_VERSION)) {
                throw CountdownDataException(CountdownDataProblem.CORRUPT)
            }

            val rawSchemaVersion = root.get(KEY_SCHEMA_VERSION)
            if (rawSchemaVersion !is Int) {
                throw CountdownDataException(CountdownDataProblem.CORRUPT)
            }
            val schemaVersion = rawSchemaVersion
            CountdownStorageSchema.problemFor(schemaVersion)?.let { problem ->
                throw CountdownDataException(problem)
            }
            if (!root.has(KEY_EVENTS)) {
                throw CountdownDataException(CountdownDataProblem.CORRUPT)
            }

            val events = root.getJSONArray(KEY_EVENTS)
            if (events.length() > CountdownValidation.MAX_EVENTS) {
                throw CountdownDataException(CountdownDataProblem.CORRUPT)
            }

            val parsed = buildList {
                for (index in 0 until events.length()) {
                    add(parseEvent(events.getJSONObject(index), schemaVersion))
                }
            }
            if (enforceImportLimits) {
                CountdownValidation.validateImportedEvents(parsed)
            } else {
                CountdownValidation.validateStoredEvents(parsed)
            }
            return parsed
        } catch (error: CountdownDataException) {
            throw error
        } catch (error: Exception) {
            throw CountdownDataException(CountdownDataProblem.CORRUPT, error)
        }
    }

    fun encode(events: List<CountdownEvent>): String {
        CountdownValidation.validateStoredEvents(events)
        val payload = JSONObject()
            .put(KEY_SCHEMA_VERSION, CountdownStorageSchema.CURRENT_VERSION)
            .put(KEY_EVENTS, JSONArray().apply {
                events.forEach { put(toJson(it)) }
            })
            .toString()
        CountdownValidation.validatePayloadSize(payload)
        return payload
    }

    private fun parseEvent(json: JSONObject, schemaVersion: Int): CountdownEvent {
        val rawType = json.getString(KEY_TYPE)
        val type = when (schemaVersion) {
            CountdownStorageSchema.LEGACY_VERSION -> EventType.fromLegacyName(rawType)
            CountdownStorageSchema.PREVIOUS_VERSION,
            CountdownStorageSchema.NOTIFICATION_VERSION,
            CountdownStorageSchema.REMINDER_VERSION,
            CountdownStorageSchema.REPEAT_RULE_VERSION,
            CountdownStorageSchema.EXPANDED_RECURRENCE_VERSION,
            CountdownStorageSchema.COUNT_MODE_VERSION,
            CountdownStorageSchema.INCLUDE_TODAY_VERSION, CountdownStorageSchema.COLOR_TIME_VERSION, CountdownStorageSchema.COUNT_COLOR_VERSION, CountdownStorageSchema.DEFAULT_COLOR_VERSION,
            -> EventType.fromStorageKey(rawType)
            else -> null
        } ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)

        val icon = when (schemaVersion) {
            CountdownStorageSchema.LEGACY_VERSION,
            CountdownStorageSchema.PREVIOUS_VERSION,
            -> EventIcon.defaultFor(type)
            CountdownStorageSchema.NOTIFICATION_VERSION ->
                EventIcon.fromStorageKey(json.optString(KEY_ICON)) ?: EventIcon.defaultFor(type)
            CountdownStorageSchema.REMINDER_VERSION,
            CountdownStorageSchema.REPEAT_RULE_VERSION,
            CountdownStorageSchema.EXPANDED_RECURRENCE_VERSION,
            CountdownStorageSchema.COUNT_MODE_VERSION,
            CountdownStorageSchema.INCLUDE_TODAY_VERSION, CountdownStorageSchema.COLOR_TIME_VERSION, CountdownStorageSchema.COUNT_COLOR_VERSION, CountdownStorageSchema.DEFAULT_COLOR_VERSION,
            -> EventIcon.fromStorageKey(json.getString(KEY_ICON))
                ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)
            else -> throw CountdownDataException(CountdownDataProblem.CORRUPT)
        }

        val reminder = when (schemaVersion) {
            CountdownStorageSchema.LEGACY_VERSION,
            CountdownStorageSchema.PREVIOUS_VERSION,
            -> ReminderOption.OFF
            CountdownStorageSchema.NOTIFICATION_VERSION ->
                if (json.optBoolean(KEY_NOTIFY_ON_ARRIVAL, false)) {
                    ReminderOption.ON_DAY
                } else {
                    ReminderOption.OFF
                }
            CountdownStorageSchema.REMINDER_VERSION,
            CountdownStorageSchema.REPEAT_RULE_VERSION,
            CountdownStorageSchema.EXPANDED_RECURRENCE_VERSION,
            CountdownStorageSchema.COUNT_MODE_VERSION,
            CountdownStorageSchema.INCLUDE_TODAY_VERSION, CountdownStorageSchema.COLOR_TIME_VERSION, CountdownStorageSchema.COUNT_COLOR_VERSION, CountdownStorageSchema.DEFAULT_COLOR_VERSION,
            -> ReminderOption.fromStorageKey(json.getString(KEY_REMINDER))
                ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)
            else -> throw CountdownDataException(CountdownDataProblem.CORRUPT)
        }

        val repeatRule = CountdownStorageSchema.repeatRuleFor(
            schemaVersion,
            if (schemaVersion >= CountdownStorageSchema.REPEAT_RULE_VERSION) {
                json.getString(KEY_REPEAT_RULE)
            } else {
                null
            },
        ) ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)

        val countMode = CountdownStorageSchema.countModeFor(
            schemaVersion,
            if (schemaVersion >= CountdownStorageSchema.COUNT_MODE_VERSION) {
                json.getString(KEY_COUNT_MODE)
            } else {
                null
            },
        ) ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)

        return CountdownEvent(
            id = json.getString(KEY_ID),
            title = json.getString(KEY_TITLE),
            date = LocalDate.parse(json.getString(KEY_DATE)),
            type = type,
            icon = icon,
            reminder = reminder,
            createdAt = Instant.parse(json.getString(KEY_CREATED_AT)),
            repeatRule = repeatRule,
            countMode = countMode,
            includeToday = if (schemaVersion >= CountdownStorageSchema.INCLUDE_TODAY_VERSION) {
                val flag = json.get("includeToday")
                if (flag !is Boolean) throw CountdownDataException(CountdownDataProblem.CORRUPT)
                flag
            } else false,
            cardColorCustom = if(json.has("cardColorCustom")) strictBoolean(json,"cardColorCustom") else false,
            countColorCustom = if(json.has("countColorCustom")) strictBoolean(json,"countColorCustom") else false,
            cardColorDefault = if (schemaVersion >= CountdownStorageSchema.DEFAULT_COLOR_VERSION) strictBoolean(json,"cardColorDefault") else false,
            countColorDefault = if (schemaVersion >= CountdownStorageSchema.DEFAULT_COLOR_VERSION) strictBoolean(json,"countColorDefault") else false,
            countColor = if (schemaVersion >= CountdownStorageSchema.COUNT_COLOR_VERSION) {
                val raw = json.get("countColor")
                if (raw !is String) throw CountdownDataException(CountdownDataProblem.CORRUPT)
                OpaqueColor.parseHex(raw) ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)
            } else OpaqueColor.BLUE,
            cardColor = if (schemaVersion >= CountdownStorageSchema.COLOR_TIME_VERSION) {
                val raw = json.get("cardColor")
                if (raw !is String) throw CountdownDataException(CountdownDataProblem.CORRUPT)
                OpaqueColor.parseHex(raw) ?: throw CountdownDataException(CountdownDataProblem.CORRUPT)
            } else OpaqueColor.WHITE,
            reminderMinuteOfDay = if (schemaVersion >= CountdownStorageSchema.COLOR_TIME_VERSION) {
                val raw = json.get("reminderMinuteOfDay")
                if (raw !is Int || raw !in 0..1439) throw CountdownDataException(CountdownDataProblem.CORRUPT)
                raw
            } else 9 * 60,
        )
    }

    private fun strictBoolean(json: JSONObject,key: String): Boolean {
        val flag=json.get(key)
        if (flag !is Boolean) throw CountdownDataException(CountdownDataProblem.CORRUPT)
        return flag
    }
    private fun toJson(event: CountdownEvent): JSONObject = JSONObject()
        .put(KEY_ID, event.id)
        .put(KEY_TITLE, event.title)
        .put(KEY_DATE, event.date.toString())
        .put(KEY_TYPE, event.type.storageKey)
        .put(KEY_ICON, event.icon.storageKey)
        .put(KEY_REMINDER, event.reminder.storageKey)
        .put(KEY_REPEAT_RULE, event.repeatRule.storageKey)
        .put(KEY_COUNT_MODE, event.countMode.storageKey)
        .put("includeToday", event.includeToday)
        .put("cardColorCustom",event.cardColorCustom).put("countColorCustom",event.countColorCustom)
        .put("cardColorDefault",event.cardColorDefault)
        .put("countColorDefault",event.countColorDefault)
        .put("cardColor", OpaqueColor.hex(event.cardColor))
        .put("countColor", OpaqueColor.hex(event.countColor))
        .put("reminderMinuteOfDay", event.reminderMinuteOfDay)
        .put(KEY_CREATED_AT, event.createdAt.toString())

    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_EVENTS = "events"
    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_DATE = "date"
    private const val KEY_TYPE = "type"
    private const val KEY_ICON = "iconKey"
    private const val KEY_REMINDER = "reminderKey"
    private const val KEY_REPEAT_RULE = "repeatRule"
    private const val KEY_COUNT_MODE = "countMode"
    private const val KEY_NOTIFY_ON_ARRIVAL = "notifyOnArrival"
    private const val KEY_CREATED_AT = "createdAt"
    private const val READ_BUFFER_BYTES = 16 * 1024
}
