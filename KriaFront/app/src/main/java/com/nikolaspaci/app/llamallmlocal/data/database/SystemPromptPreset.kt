package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_prompt_presets")
data class SystemPromptPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val prompt: String,
    val createdAt: Long = System.currentTimeMillis()
)
