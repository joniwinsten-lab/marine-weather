package fi.veneappi.app.data.marine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineTextSummarizerTest {
    @Test
    fun `prioritises warning sentences`() {
        val raw =
            """
            Forecast for Baltic Sea valid 24 hours.
            No warnings.
            Northern Baltic: southerly 4-10 m/s. Rain showers locally.
            """.trimIndent()
        val out = MarineTextSummarizer.summarize(raw)
        assertTrue(out.contains("warning", ignoreCase = true))
        assertTrue(out.length >= 20)
    }

    @Test
    fun `returns up to two sentences from long prose`() {
        val raw =
            "First sentence about wind. Second sentence about visibility. Third should be dropped."
        val out = MarineTextSummarizer.summarize(raw, maxSentences = 2)
        assertEquals(
            "First sentence about wind. Second sentence about visibility.",
            out,
        )
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", MarineTextSummarizer.summarize("   "))
    }

    @Test
    fun `summarizes met norway sea area text`() {
        val raw =
            """
            North Cape Bank

            Forecast for North Cape Bank from Friday, 15 May 22:00 to Sunday, 17 May 10:00

            South 4. Slight, occasionally rough. Dry and good.
            """.trimIndent()
        val out = MarineTextSummarizer.summarize(raw)
        assertTrue(out.length >= 20)
    }
}
