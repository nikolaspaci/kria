package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add new fields to conversations
            db.execSQL("ALTER TABLE conversations ADD COLUMN title TEXT")
            db.execSQL("ALTER TABLE conversations ADD COLUMN lastMessagePreview TEXT")
            db.execSQL("ALTER TABLE conversations ADD COLUMN lastUpdatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE conversations ADD COLUMN messageCount INTEGER NOT NULL DEFAULT 0")

            // Create indices for chat_messages
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId ON chat_messages(conversationId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_timestamp ON chat_messages(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_timestamp ON chat_messages(conversationId, timestamp)")

            // Update existing data
            db.execSQL("""
                UPDATE conversations
                SET lastUpdatedAt = timestamp,
                    messageCount = (
                        SELECT COUNT(*) FROM chat_messages
                        WHERE chat_messages.conversationId = conversations.id
                    )
            """)
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN contextSize INTEGER NOT NULL DEFAULT 2048")
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN maxTokens INTEGER NOT NULL DEFAULT 256")
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN threadCount INTEGER NOT NULL DEFAULT 4")
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN repeatPenalty REAL NOT NULL DEFAULT 1.1")
            db.execSQL("ALTER TABLE model_parameters ADD COLUMN useGpu INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4)
}
