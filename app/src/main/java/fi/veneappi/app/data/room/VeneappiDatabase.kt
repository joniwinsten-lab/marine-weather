package fi.veneappi.app.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ForecastCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VeneappiDatabase : RoomDatabase() {
    abstract fun forecastCacheDao(): ForecastCacheDao
}
