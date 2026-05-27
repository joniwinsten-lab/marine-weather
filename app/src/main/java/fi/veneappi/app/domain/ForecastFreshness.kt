package fi.veneappi.app.domain

enum class ForecastStaleLevel {
    Fresh,
    Soft,
    Hard,
}

object ForecastFreshness {
    const val SOFT_STALE_MS = 6L * 60 * 60 * 1000
    const val HARD_STALE_MS = 12L * 60 * 60 * 1000

    fun staleLevel(
        fetchedAtUtc: Long,
        nowUtc: Long = System.currentTimeMillis(),
    ): ForecastStaleLevel {
        val age = (nowUtc - fetchedAtUtc).coerceAtLeast(0L)
        return when {
            age >= HARD_STALE_MS -> ForecastStaleLevel.Hard
            age >= SOFT_STALE_MS -> ForecastStaleLevel.Soft
            else -> ForecastStaleLevel.Fresh
        }
    }

    fun oldestFetchedUtc(forecasts: Map<SourceId, Result<UnifiedForecast>>): Long? =
        forecasts.values
            .mapNotNull { it.getOrNull()?.fetchedAtUtc }
            .minOrNull()
}
