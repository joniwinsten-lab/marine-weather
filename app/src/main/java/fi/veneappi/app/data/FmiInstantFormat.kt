package fi.veneappi.app.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** FMI WFS/WMS reject [Instant.toString] fractional seconds (HTTP 400). */
object FmiInstantFormat {
    private val HH_MM_UTC: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

    private val YMD_HM_UTC: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

    fun toParam(instant: Instant): String =
        instant.truncatedTo(ChronoUnit.SECONDS).toString()

    fun toDisplayHHmm(instant: Instant): String = HH_MM_UTC.format(instant)

    fun toDisplayYmdHm(instant: Instant): String = YMD_HM_UTC.format(instant)
}
