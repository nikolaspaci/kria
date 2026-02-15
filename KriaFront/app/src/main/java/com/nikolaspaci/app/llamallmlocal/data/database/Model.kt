package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "models",
    indices = [Index(value = ["filePath"], unique = true)]
)
data class Model(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val defaultParameterId: Long? = null,
    val pendingParameterId: Long? = null
)
