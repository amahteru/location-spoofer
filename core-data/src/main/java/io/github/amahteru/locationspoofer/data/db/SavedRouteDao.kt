package io.github.amahteru.locationspoofer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_routes ORDER BY timestamp DESC")
    fun getAllSavedRoutes(): Flow<List<SavedRouteEntity>>

    @Query("SELECT * FROM saved_routes ORDER BY timestamp DESC")
    suspend fun getAllSavedRoutesList(): List<SavedRouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedRoute(route: SavedRouteEntity)

    @Query("UPDATE saved_routes SET name = :name WHERE id = :id")
    suspend fun renameSavedRoute(id: Long, name: String)

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun deleteSavedRoute(id: Long)
}
