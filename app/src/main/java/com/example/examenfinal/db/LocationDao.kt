package com.example.examenfinal.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert
    suspend fun insertLocation(location: LocationEntity)

    // Para dibujar la ruta y ver historial [cite: 26, 45]
    @Query("SELECT * FROM location_history ORDER BY timestamp DESC")
    fun getAllLocations(): Flow<List<LocationEntity>>

    // Opción para limpiar historial [cite: 52]
    @Query("DELETE FROM location_history")
    suspend fun clearHistory()
}