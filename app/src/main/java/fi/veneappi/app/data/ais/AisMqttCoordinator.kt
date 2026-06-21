package fi.veneappi.app.data.ais

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single Digitraffic MQTT connection shared by map overlay and Seuranta tab.
 * Subscriptions are the union of active consumers' MMSI sets.
 */
class AisMqttCoordinator(
    private val scope: CoroutineScope,
) {
    private val client = DigitrafficAisMqttClient()
    private val subscriptionManager = AisSubscriptionManager(client)
    private val reconcileMutex = Mutex()

    private var viewportActive = false
    private var watchlistActive = false
    private var viewportMmsis: Set<Int> = emptySet()
    private var watchlistMmsis: Set<Int> = emptySet()

    val connectionState: StateFlow<AisMqttConnectionState> = client.connectionState
    val messages: SharedFlow<AisMqttInboundMessage> = client.messages
    val isConnected: Boolean
        get() = client.isConnected

    fun setViewportConsumer(
        active: Boolean,
        mmsis: Set<Int>,
    ) {
        scope.launch {
            reconcileMutex.withLock {
                viewportActive = active
                viewportMmsis = mmsis
                reconcileLocked()
            }
        }
    }

    fun setWatchlistConsumer(
        active: Boolean,
        mmsis: Set<Int>,
    ) {
        scope.launch {
            reconcileMutex.withLock {
                watchlistActive = active
                watchlistMmsis = mmsis
                reconcileLocked()
            }
        }
    }

    fun disconnectAll() {
        scope.launch {
            reconcileMutex.withLock {
                viewportActive = false
                watchlistActive = false
                viewportMmsis = emptySet()
                watchlistMmsis = emptySet()
                subscriptionManager.clearLocalState()
                runCatching { client.disconnect() }
            }
        }
    }

    private suspend fun reconcileLocked() {
        val shouldConnect = viewportActive || watchlistActive
        if (!shouldConnect) {
            subscriptionManager.clearLocalState()
            runCatching { client.disconnect() }
            return
        }

        val union =
            buildSet {
                if (viewportActive) addAll(viewportMmsis)
                if (watchlistActive) addAll(watchlistMmsis)
            }

        runCatching {
            if (!client.isConnected) {
                client.connect()
            }
            subscriptionManager.sync(union)
            log("subscriptions=${subscriptionManager.subscribedCount} union=${union.size}")
        }.onFailure { e ->
            Log.w(TAG, "mqtt reconcile failed", e)
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    companion object {
        private const val TAG = "AisMqttCoordinator"
    }
}
