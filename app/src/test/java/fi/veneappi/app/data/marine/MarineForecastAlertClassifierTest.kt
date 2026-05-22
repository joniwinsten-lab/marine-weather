package fi.veneappi.app.data.marine

import org.junit.Assert.assertEquals
import org.junit.Test

class MarineForecastAlertClassifierTest {
    @Test
    fun `met structured wind warning`() {
        val level =
            MarineForecastAlertClassifier.classify(
                rawText = "South 4. Slight.",
                metWindWarning = "North gale 8 from Saturday 01utc until 14utc",
            )
        assertEquals(MarineForecastAlertLevel.Warning, level)
    }

    @Test
    fun `ee no warnings with fog is notice`() {
        val raw =
            """
            No warnings.

            Northern Baltic: southerly 4-10 m/s. Visibility mainly good, at night possible fog patches.
            """.trimIndent()
        assertEquals(MarineForecastAlertLevel.Notice, MarineForecastAlertClassifier.classify(raw))
    }

    @Test
    fun `ee no warnings only is none`() {
        val raw = "No warnings.\n\nWeather summary: weak trough."
        assertEquals(MarineForecastAlertLevel.None, MarineForecastAlertClassifier.classify(raw))
    }

    @Test
    fun `gale in text is warning`() {
        val raw = "Northwest near gale 7, occasionally gale 8. Very rough."
        assertEquals(MarineForecastAlertLevel.Warning, MarineForecastAlertClassifier.classify(raw))
    }

    @Test
    fun `point summary is none`() {
        val raw = "At the map centre: 5.2 m/s from SW, gusts to 12 m/s. Air about 14 °C."
        assertEquals(MarineForecastAlertLevel.None, MarineForecastAlertClassifier.classify(raw))
    }
}
