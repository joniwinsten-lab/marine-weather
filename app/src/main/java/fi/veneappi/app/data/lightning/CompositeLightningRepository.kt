package fi.veneappi.app.data.lightning

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class CompositeLightningRepository(
    private val fmi: FmiLightningRepository,
    private val smhi: SmhiLightningRepository,
) {
    suspend fun fetchMergedStrikes(): Result<List<LightningStrike>> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val fmiDeferred = async { fmi.fetchRecentStrikes() }
                val smhiDeferred = async { smhi.fetchRecentStrikes() }
                val fmiResult = fmiDeferred.await()
                val smhiResult = smhiDeferred.await()
                val fmiStrikes = fmiResult.getOrElse { emptyList() }
                val smhiStrikes = smhiResult.getOrElse { emptyList() }
                val merged =
                    (fmiStrikes + smhiStrikes)
                        .distinctBy { "${it.source}:${it.latitude}:${it.longitude}:${it.observedAtEpochMs}" }
                        .sortedByDescending { it.observedAtEpochMs }
                val errors =
                    listOfNotNull(
                        fmiResult.exceptionOrNull()?.message,
                        smhiResult.exceptionOrNull()?.message,
                    )
                if (merged.isEmpty() && errors.isNotEmpty()) {
                    Result.failure(IllegalStateException(errors.distinct().joinToString(" · ")))
                } else {
                    Result.success(merged)
                }
            }
        }
}
