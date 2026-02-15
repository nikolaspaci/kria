package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemPromptPresetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: SystemPromptPreset): Long

    @Delete
    suspend fun delete(preset: SystemPromptPreset)

    @Query("SELECT * FROM system_prompt_presets ORDER BY createdAt DESC")
    fun getAllPresets(): Flow<List<SystemPromptPreset>>

    @Query("SELECT * FROM system_prompt_presets WHERE id = :id")
    suspend fun getById(id: Long): SystemPromptPreset?
}
