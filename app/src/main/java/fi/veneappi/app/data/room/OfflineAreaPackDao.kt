package fi.veneappi.app.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineAreaPackDao {
    @Query("SELECT * FROM offline_area_pack ORDER BY created_at_ms DESC LIMIT 1")
    suspend fun latest(): OfflineAreaPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OfflineAreaPackEntity): Long
}
