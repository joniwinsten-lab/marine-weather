package fi.veneappi.app.data.ais

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps MQTT topic subscriptions aligned with viewport MMSI set. */
class AisSubscriptionManager(
    private val mqttClient: DigitrafficAisMqttClient,
) {
    private val mutex = Mutex()
    private val subscribedMmsis = mutableSetOf<Int>()

    val subscribedCount: Int
        get() = subscribedMmsis.size

    suspend fun sync(wanted: Set<Int>) {
        mutex.withLock {
            if (!mqttClient.isConnected) return
            val toRemove = subscribedMmsis - wanted
            val toAdd = wanted - subscribedMmsis
            for (mmsi in toRemove) {
                mqttClient.unsubscribeVessel(mmsi)
                subscribedMmsis.remove(mmsi)
            }
            for (mmsi in toAdd) {
                mqttClient.subscribeVessel(mmsi)
                subscribedMmsis.add(mmsi)
            }
        }
    }

    /** Clears local tracking after broker disconnect (broker drops subscriptions). */
    fun clearLocalState() {
        subscribedMmsis.clear()
    }

    companion object {
        /** Pure delta for tests. */
        fun subscriptionDelta(
            subscribed: Set<Int>,
            wanted: Set<Int>,
        ): Pair<Set<Int>, Set<Int>> = (subscribed - wanted) to (wanted - subscribed)
    }
}
