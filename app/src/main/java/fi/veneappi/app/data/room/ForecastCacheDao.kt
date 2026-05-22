package fi.veneappi.app.data.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "forecast_cache")
data class ForecastCacheEntity(
    @PrimaryKey @androidx.room.ColumnInfo(name = "cache_key") val cacheKey: String,
    val json: String,
    val updatedAt: Long,
)

@Dao
interface ForecastCacheDao {
    @Query("SELECT * FROM forecast_cache WHERE cache_key = :cacheKey LIMIT 1")
    suspend fun get(cacheKey: String): ForecastCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ForecastCacheEntity)
}
