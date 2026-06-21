package fi.veneappi.app.data.ais

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import fi.veneappi.app.domain.ais.AisMqttConfig
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class AisMqttInboundMessage(
    val topic: String,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AisMqttInboundMessage
        return topic == other.topic && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/** Digitraffic AIS MQTT over WebSockets (HiveMQ client). */
class DigitrafficAisMqttClient {
    private val clientRef = AtomicReference<Mqtt3AsyncClient?>(null)
    private val operationMutex = Mutex()

    private val _connectionState =
        MutableStateFlow(AisMqttConnectionState.Disconnected)
    val connectionState: StateFlow<AisMqttConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<AisMqttInboundMessage>(extraBufferCapacity = 512)
    val messages: SharedFlow<AisMqttInboundMessage> = _messages.asSharedFlow()

    val isConnected: Boolean
        get() = clientRef.get()?.state?.isConnected == true

    suspend fun connect() {
        operationMutex.withLock {
            if (isConnected) {
                _connectionState.value = AisMqttConnectionState.Connected
                return
            }
            _connectionState.value = AisMqttConnectionState.Connecting
            withContext(Dispatchers.IO) {
                runCatching {
                    val existing = clientRef.get()
                    if (existing != null && !existing.state.isConnected) {
                        runCatching { existing.disconnect().get(3, TimeUnit.SECONDS) }
                        clientRef.set(null)
                    }

                    val clientId =
                        "marine-weather-android-${Random.nextInt(0, Int.MAX_VALUE).toString(16)}"
                    val client =
                        MqttClient.builder()
                            .useMqttVersion3()
                            .identifier(clientId)
                            .serverHost(BROKER_HOST)
                            .serverPort(BROKER_PORT)
                            .sslWithDefaultConfig()
                            .webSocketConfig()
                            .serverPath(WEBSOCKET_PATH)
                            .subprotocol("mqtt")
                            .applyWebSocketConfig()
                            .simpleAuth()
                            .username(AisMqttConfig.USERNAME)
                            .password(AisMqttConfig.PASSWORD.toByteArray())
                            .applySimpleAuth()
                            .automaticReconnect()
                            .initialDelay(AisMqttConfig.RECONNECT_PERIOD_MS, TimeUnit.MILLISECONDS)
                            .applyAutomaticReconnect()
                            .addConnectedListener {
                                _connectionState.value = AisMqttConnectionState.Connected
                                log("connected")
                            }
                            .addDisconnectedListener { context ->
                                _connectionState.value = AisMqttConnectionState.Disconnected
                                log("disconnected: ${context.cause?.message ?: "closed"}")
                            }
                            .buildAsync()

                    client.publishes(MqttGlobalPublishFilter.ALL) { publish ->
                        val payload = publish.payloadAsBytes
                        if (payload.isEmpty()) return@publishes
                        _messages.tryEmit(
                            AisMqttInboundMessage(
                                topic = publish.topic.toString(),
                                payload = payload,
                            ),
                        )
                    }

                    clientRef.set(client)
                    client.connect()
                        .get(AisMqttConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    _connectionState.value = AisMqttConnectionState.Connected
                }.onFailure { e ->
                    _connectionState.value = AisMqttConnectionState.Error
                    Log.w(TAG, "connect failed", e)
                    throw e
                }
            }
        }
    }

    suspend fun disconnect() {
        operationMutex.withLock {
            withContext(Dispatchers.IO) {
                val client = clientRef.getAndSet(null) ?: return@withContext
                runCatching {
                    client.disconnect().get(3, TimeUnit.SECONDS)
                }.onFailure { e ->
                    Log.w(TAG, "disconnect failed", e)
                }
            }
            _connectionState.value = AisMqttConnectionState.Disconnected
        }
    }

    suspend fun subscribeVessel(mmsi: Int) {
        if (!isConnected) return
        withContext(Dispatchers.IO) {
            val client = clientRef.get() ?: return@withContext
            val qos = MqttQos.AT_LEAST_ONCE
            runCatching {
                client.subscribeWith()
                    .topicFilter(AisMqttConfig.locationTopic(mmsi))
                    .qos(qos)
                    .send()
                    .get(5, TimeUnit.SECONDS)
                client.subscribeWith()
                    .topicFilter(AisMqttConfig.metadataTopic(mmsi))
                    .qos(qos)
                    .send()
                    .get(5, TimeUnit.SECONDS)
            }.onFailure { e ->
                Log.w(TAG, "subscribe $mmsi failed", e)
            }
        }
    }

    suspend fun unsubscribeVessel(mmsi: Int) {
        if (!isConnected) return
        withContext(Dispatchers.IO) {
            val client = clientRef.get() ?: return@withContext
            runCatching {
                client.unsubscribeWith()
                    .topicFilter(AisMqttConfig.locationTopic(mmsi))
                    .send()
                    .get(5, TimeUnit.SECONDS)
                client.unsubscribeWith()
                    .topicFilter(AisMqttConfig.metadataTopic(mmsi))
                    .send()
                    .get(5, TimeUnit.SECONDS)
            }.onFailure { e ->
                Log.w(TAG, "unsubscribe $mmsi failed", e)
            }
        }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    companion object {
        private const val TAG = "DigitrafficAisMqtt"
        private const val BROKER_HOST = "meri.digitraffic.fi"
        private const val BROKER_PORT = 443
        private const val WEBSOCKET_PATH = "/mqtt"
    }
}
