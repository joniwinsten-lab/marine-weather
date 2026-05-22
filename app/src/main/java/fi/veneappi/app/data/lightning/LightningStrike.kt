package fi.veneappi.app.data.lightning

data class LightningStrike(
    val latitude: Double,
    val longitude: Double,
    val observedAtEpochMs: Long,
    val source: LightningSourceId = LightningSourceId.FMI,
)
