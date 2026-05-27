package fi.veneappi.app.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_area_pack")
data class OfflineAreaPackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "route_point_count") val routePointCount: Int,
    @ColumnInfo(name = "weather_sample_count") val weatherSampleCount: Int,
    @ColumnInfo(name = "min_lat") val minLat: Double,
    @ColumnInfo(name = "min_lon") val minLon: Double,
    @ColumnInfo(name = "max_lat") val maxLat: Double,
    @ColumnInfo(name = "max_lon") val maxLon: Double,
    @ColumnInfo(name = "label") val label: String,
)
