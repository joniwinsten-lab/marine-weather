package fi.veneappi.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager

@SuppressLint("MissingPermission")
internal fun readLastKnownLatLon(context: Context): Pair<Double, Double>? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers =
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    var best: Location? = null
    for (p in providers) {
        val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
        val prev = best
        if (prev == null || loc.elapsedRealtimeNanos > prev.elapsedRealtimeNanos) {
            best = loc
        }
    }
    return best?.let { it.latitude to it.longitude }
}
