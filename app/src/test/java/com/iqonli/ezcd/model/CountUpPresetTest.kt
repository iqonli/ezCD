package com.iqonli.ezcd.model

import org.junit.Assert.*
import org.junit.Test

class CountUpPresetTest {
    @Test fun catalogHasSevenStartingPointsAndTwoGenericChoices() {
        assertEquals(listOf("SMOKE_FREE", "NEW_HABIT", "TRAINING", "READING", "PROJECT",
            "FRESH_START", "MILESTONE", "EVENT", "CUSTOM"), CountUpPreset.entries.map { it.name })
        assertEquals(7, CountUpPreset.entries.count { it.suggestsTitle })
    }

    @Test fun templatesOnlyUseTypesAndIconsHandledByTheBaselineEditor() {
        CountUpPreset.entries.forEach {
            assertTrue(it.type.storageKey in setOf("event", "custom"))
            if (it.type == EventType.CUSTOM) {
                assertTrue(it.icon in EventIcon.customChoices)
            } else {
                assertEquals(EventIcon.defaultFor(EventType.EVENT), it.icon)
            }
        }
    }

    @Test fun suggestionsNeverReplaceNonblankDraftText() {
        CountUpPreset.entries.forEach {
            assertEquals("My own title 👩🏽‍🚀", it.titleFor("My own title 👩🏽‍🚀", "Suggested"))
            assertEquals("  Keep spaces  ", it.titleFor("  Keep spaces  ", "Suggested"))
            assertEquals(if (it.suggestsTitle) "Suggested" else "", it.titleFor("", "Suggested"))
        }
    }

    @Test fun savedTypesDoNotInferSpecificOrSensitiveTemplateIdentity() {
        assertEquals(CountUpPreset.CUSTOM, CountUpPreset.forStoredType(EventType.CUSTOM))
        assertEquals(CountUpPreset.EVENT, CountUpPreset.forStoredType(EventType.EVENT))
        EventType.entries.filter { it !in setOf(EventType.EVENT, EventType.CUSTOM) }.forEach {
            assertNull(CountUpPreset.forStoredType(it))
        }
    }
}
