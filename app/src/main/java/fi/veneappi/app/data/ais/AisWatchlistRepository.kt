package fi.veneappi.app.data.ais

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fi.veneappi.app.domain.ais.AddedSource
import fi.veneappi.app.domain.ais.AisTrackConfig
import fi.veneappi.app.domain.ais.AisWatchlistEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.aisWatchlistStore: DataStore<Preferences> by preferencesDataStore(
    name = "ais_watchlist",
)

class WatchlistFullException(
    val maxCount: Int,
) : Exception("Watchlist full (max $maxCount)")

class WatchlistAlreadyExistsException(
    val mmsi: Int,
) : Exception("MMSI $mmsi already in watchlist")

class AisWatchlistRepository(
    private val context: Context,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
) {
    private val storageKey = stringPreferencesKey(AisTrackConfig.WATCHLIST_STORAGE_KEY)
    private val writeMutex = Mutex()

    val entries: Flow<List<AisWatchlistEntry>> =
        context.aisWatchlistStore.data.map { prefs ->
            decodeEntries(prefs[storageKey])
        }

    suspend fun addEntry(
        mmsi: Int,
        nickname: String? = null,
        name: String? = null,
        callSign: String? = null,
        source: AddedSource = AddedSource.MANUAL,
    ): Result<Unit> =
        writeMutex.withLock {
            val current = readEntriesLocked()
            if (current.any { it.mmsi == mmsi }) {
                return Result.failure(WatchlistAlreadyExistsException(mmsi))
            }
            if (current.size >= AisTrackConfig.MAX_WATCHLIST_VESSELS) {
                return Result.failure(WatchlistFullException(AisTrackConfig.MAX_WATCHLIST_VESSELS))
            }
            val updated =
                current +
                    AisWatchlistEntry(
                        mmsi = mmsi,
                        nickname = nickname?.trim()?.ifEmpty { null },
                        name = name?.trim()?.ifEmpty { null },
                        callSign = callSign?.trim()?.ifEmpty { null },
                        addedAtEpochMs = System.currentTimeMillis(),
                        addedSource = source.name,
                    )
            persistLocked(updated)
            Result.success(Unit)
        }

    suspend fun removeEntry(mmsi: Int) {
        writeMutex.withLock {
            persistLocked(readEntriesLocked().filterNot { it.mmsi == mmsi })
        }
    }

    suspend fun updateNickname(
        mmsi: Int,
        nickname: String?,
    ) {
        writeMutex.withLock {
            val updated =
                readEntriesLocked().map { entry ->
                    if (entry.mmsi == mmsi) {
                        entry.copy(nickname = nickname?.trim()?.ifEmpty { null })
                    } else {
                        entry
                    }
                }
            persistLocked(updated)
        }
    }

    private suspend fun readEntriesLocked(): List<AisWatchlistEntry> {
        val raw = context.aisWatchlistStore.data.first()[storageKey]
        return decodeEntries(raw)
    }

    private suspend fun persistLocked(entries: List<AisWatchlistEntry>) {
        val payload =
            json.encodeToString(
                entries.map { entry ->
                    StoredWatchlistEntry(
                        mmsi = entry.mmsi,
                        nickname = entry.nickname,
                        name = entry.name,
                        callSign = entry.callSign,
                        destination = entry.destination,
                        addedAtEpochMs = entry.addedAtEpochMs,
                        addedSource = entry.addedSource,
                    )
                },
            )
        context.aisWatchlistStore.edit { prefs ->
            prefs[storageKey] = payload
        }
    }

    private fun decodeEntries(raw: String?): List<AisWatchlistEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<StoredWatchlistEntry>>(raw).mapNotNull { stored ->
                if (stored.mmsi <= 0) return@mapNotNull null
                AisWatchlistEntry(
                    mmsi = stored.mmsi,
                    nickname = stored.nickname,
                    name = stored.name,
                    callSign = stored.callSign,
                    destination = stored.destination,
                    addedAtEpochMs = stored.addedAtEpochMs ?: System.currentTimeMillis(),
                    addedSource = stored.addedSource ?: AddedSource.MANUAL.name,
                )
            }
        }.getOrElse { emptyList() }
    }
}

@Serializable
private data class StoredWatchlistEntry(
    val mmsi: Int,
    val nickname: String? = null,
    val name: String? = null,
    val callSign: String? = null,
    val destination: String? = null,
    val addedAtEpochMs: Long? = null,
    val addedSource: String? = null,
)
