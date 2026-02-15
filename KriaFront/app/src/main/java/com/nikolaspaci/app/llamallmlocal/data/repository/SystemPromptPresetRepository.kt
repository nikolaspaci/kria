package com.nikolaspaci.app.llamallmlocal.data.repository

import com.nikolaspaci.app.llamallmlocal.data.database.SystemPromptPreset
import com.nikolaspaci.app.llamallmlocal.data.database.SystemPromptPresetDao
import kotlinx.coroutines.flow.Flow

class SystemPromptPresetRepository(private val dao: SystemPromptPresetDao) {

    fun getAllPresets(): Flow<List<SystemPromptPreset>> = dao.getAllPresets()

    suspend fun insert(preset: SystemPromptPreset): Long = dao.insert(preset)

    suspend fun delete(preset: SystemPromptPreset) = dao.delete(preset)

    suspend fun getById(id: Long): SystemPromptPreset? = dao.getById(id)
}
