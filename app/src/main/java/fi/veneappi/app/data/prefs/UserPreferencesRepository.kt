package fi.veneappi.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fi.veneappi.app.domain.WindUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "veneappi_prefs")

private const val ROUTE_TRIAL_MS = 3L * 24 * 60 * 60 * 1000

class UserPreferencesRepository(
    private val context: Context,
) {
    private val windKey = stringPreferencesKey("wind_unit")
    private val routeTrialStartKey = longPreferencesKey("route_trial_start_epoch_ms")

    val windUnit: Flow<WindUnit> =
        context.dataStore.data.map { prefs ->
            when (prefs[windKey]) {
                WindUnit.Knots.name -> WindUnit.Knots
                else -> WindUnit.MetersPerSecond
            }
        }

    suspend fun setWindUnit(unit: WindUnit) {
        context.dataStore.edit { it[windKey] = unit.name }
    }

    /** True while local 3-day route trial is active (does not involve Google Play billing). */
    val routeTrialActive: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            val start = prefs[routeTrialStartKey] ?: 0L
            if (start <= 0L) return@map false
            System.currentTimeMillis() < start + ROUTE_TRIAL_MS
        }

    /** True if the user has ever started the one-time local trial (even if it has expired). */
    val routeTrialWasStarted: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            (prefs[routeTrialStartKey] ?: 0L) > 0L
        }

    /**
     * Starts the local 3-day trial once per install. Does not charge Play.
     * @return false if a trial was already recorded.
     */
    suspend fun startRouteTrialIfEligible(): Boolean {
        var started = false
        context.dataStore.edit { prefs ->
            if ((prefs[routeTrialStartKey] ?: 0L) > 0L) return@edit
            prefs[routeTrialStartKey] = System.currentTimeMillis()
            started = true
        }
        return started
    }
}
