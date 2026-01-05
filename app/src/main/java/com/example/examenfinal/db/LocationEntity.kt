package com.example.examenfinal.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_history")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double, //
    val longitude: Double, //
    val timestamp: Long, //
    val accuracy: Float // Precisión [cite: 36, 51]
)