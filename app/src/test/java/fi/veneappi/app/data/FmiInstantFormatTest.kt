package fi.veneappi.app.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FmiInstantFormatTest {
    @Test
    fun strips_fractional_seconds() {
        val instant = Instant.parse("2026-05-16T06:27:07.804913Z")
        val param = FmiInstantFormat.toParam(instant)
        assertEquals("2026-05-16T06:27:07Z", param)
        assertFalse(param.contains("."))
    }

    @Test
    fun formats_instant_as_hhmm_without_crash() {
        val instant = Instant.parse("2026-05-16T14:30:00Z")
        assertEquals("14:30", FmiInstantFormat.toDisplayHHmm(instant))
    }
}
