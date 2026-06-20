package fi.veneappi.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val CURRENT_LOCATION_TIMEOUT_MS = 15_000L
/** Cached fallback only if younger than this (ms). */
private const val MAX_CACHED_AGE_MS = 2 * 60 * 1000L
/** Reject coarse network fixes worse than this (metres). */
private const val MAX_NETWORK_ACCURACY_M = 150f

internal fun hasLocationPermission(context: Context): Boolean {
    val fine =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val coarse =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

/**
 * Requests a fresh device location (Fused Location, high accuracy), then falls back to a recent
 * cached fix only if it is still fresh. Returns null when permission is missing or no usable fix.
 */
@SuppressLint("MissingPermission")
suspend fun readCurrentLatLon(context: Context): Pair<Double, Double>? {
    if (!hasLocationPermission(context)) return null
    val appContext = context.applicationContext
    val fused = LocationServices.getFusedLocationProviderClient(appContext)
    val current =
        try {
            withTimeout(CURRENT_LOCATION_TIMEOUT_MS) {
                val request =
                    CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(0)
                        .setDurationMillis(CURRENT_LOCATION_TIMEOUT_MS)
                        .build()
                fused.getCurrentLocation(request, CancellationTokenSource().token).await()
            }
        } catch (_: Exception) {
            null
        }
    pickIfUsable(current)?.let { return it.latitude to it.longitude }
    return readFreshCachedLatLon(appContext)
}

internal fun pickIfUsable(location: Location?): Location? {
    if (location == null) return null
    if (!location.latitude.isFinite() || !location.longitude.isFinite()) return null
    if (locationAgeMs(location) > MAX_CACHED_AGE_MS) return null
    if (location.provider == LocationManager.NETWORK_PROVIDER &&
        location.hasAccuracy() &&
        location.accuracy > MAX_NETWORK_ACCURACY_M
    ) {
        return null
    }
    return location
}

internal fun locationAgeMs(location: Location): Long {
    val elapsedAge = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
    if (elapsedAge >= 0) return elapsedAge
    return (System.currentTimeMillis() - location.time).coerceAtLeast(0)
}

@SuppressLint("MissingPermission")
private fun readFreshCachedLatLon(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers =
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    var best: Location? = null
    for (provider in providers) {
        val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: continue
        val usable = pickIfUsable(loc) ?: continue
        val prev = best
        if (prev == null || scoreLocation(usable) > scoreLocation(prev)) {
            best = usable
        }
    }
    return best?.let { it.latitude to it.longitude }
}

private fun scoreLocation(loc: Location): Long {
    var score = 0L
    if (loc.provider == LocationManager.GPS_PROVIDER) score += 1_000_000
    if (loc.hasAccuracy()) score += (1000 - loc.accuracy.coerceAtMost(1000f)).toLong()
    score -= locationAgeMs(loc) / 1000
    return score
}
