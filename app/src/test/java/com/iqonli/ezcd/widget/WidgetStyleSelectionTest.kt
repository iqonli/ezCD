package com.iqonli.ezcd.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetStyleSelectionTest {
    @Test
    fun factoryStyleIsClassicSystem() {
        assertEquals(WidgetAppearance.SYSTEM, WidgetStyleSelection.FACTORY.appearance)
        assertEquals(WidgetBackground.CLASSIC, WidgetStyleSelection.FACTORY.background)
    }

    @Test
    fun newConfigurationUsesSavedDefaults() {
        val defaults = WidgetStyleSelection(
            appearance = WidgetAppearance.LIGHT,
            background = WidgetBackground.MONOGRAM,
        )

        assertEquals(defaults, WidgetStyleSelectionResolver.initial(existing = null, defaults = defaults))
    }

    @Test
    fun existingConfigurationWinsOverCurrentDefaults() {
        val existing = WidgetStyleSelection(
            appearance = WidgetAppearance.DARK,
            background = WidgetBackground.FOREST,
        )
        val defaults = WidgetStyleSelection(
            appearance = WidgetAppearance.LIGHT,
            background = WidgetBackground.MONOGRAM,
        )

        assertEquals(existing, WidgetStyleSelectionResolver.initial(existing, defaults))
    }

    @Test
    fun restoredDraftWinsOverExistingConfiguration() {
        val existing = WidgetStyleSelection(
            appearance = WidgetAppearance.DARK,
            background = WidgetBackground.FOREST,
        )
        val restored = WidgetStyleSelectionResolver.restore(
            baseline = existing,
            appearanceName = WidgetAppearance.LIGHT.name,
            backgroundName = WidgetBackground.MONOGRAM.name,
        )

        assertEquals(WidgetAppearance.LIGHT, restored.appearance)
        assertEquals(WidgetBackground.MONOGRAM, restored.background)
    }

    @Test
    fun invalidRestoredFieldsFallBackIndependentlyToBaseline() {
        val baseline = WidgetStyleSelection(
            appearance = WidgetAppearance.DARK,
            background = WidgetBackground.FOREST,
        )

        assertEquals(
            WidgetStyleSelection(
                appearance = WidgetAppearance.DARK,
                background = WidgetBackground.MONOGRAM,
            ),
            WidgetStyleSelectionResolver.restore(
                baseline = baseline,
                appearanceName = "future_appearance",
                backgroundName = WidgetBackground.MONOGRAM.name,
            ),
        )
        assertEquals(
            WidgetStyleSelection(
                appearance = WidgetAppearance.LIGHT,
                background = WidgetBackground.FOREST,
            ),
            WidgetStyleSelectionResolver.restore(
                baseline = baseline,
                appearanceName = WidgetAppearance.LIGHT.name,
                backgroundName = null,
            ),
        )
    }
}
