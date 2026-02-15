# Phase 3: Persistance des Données

## Objectif
Amélioration du schéma de base de données et implémentation de la pagination.

---

## 1. Dépendances Paging 3

### libs.versions.toml

```toml
[versions]
paging = "3.3.0"

[libraries]
androidx-paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
androidx-room-paging = { group = "androidx.room", name = "room-paging", version.ref = "room" }
```

### build.gradle.kts

```kotlin
dependencies {
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.paging)
}
```

---

## 2. Enrichissement du Schéma

### 2.1 Modifier Conversation.kt

```kotlin
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modelPath: String,
    val timestamp: Long = System.currentTimeMillis(),
    // Nouveaux champs
    val title: String? = null,
    val lastMessagePreview: String? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)
```

### 2.2 Ajouter des Index à ChatMessage.kt

```kotlin
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["timestamp"]),
        Index(value = ["conversationId", "timestamp"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val sender: Sender,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## 3. Migrations Room

### Fichier à créer: `data/database/DatabaseMigrations.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Ajouter les nouveaux champs à conversations
            db.execSQL("ALTER TABLE conversations ADD COLUMN title TEXT")
            db.execSQL("ALTER TABLE conversations ADD COLUMN lastMessagePreview TEXT")
            db.execSQL("ALTER TABLE conversations ADD COLUMN lastUpdatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE conversations ADD COLUMN messageCount INTEGER NOT NULL DEFAULT 0")

            // Créer les index pour chat_messages
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId ON chat_messages(conversationId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_timestamp ON chat_messages(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId_timestamp ON chat_messages(conversationId, timestamp)")

            // Mettre à jour les données existantes
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

    val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3)
}
```

### Modifier AppDatabase.kt

```kotlin
@Database(
    entities = [Conversation::class, ChatMessage::class, ModelParameter::class],
    version = 3,  // Incrémenté
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun modelParameterDao(): ModelParameterDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_database"
                )
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

---

## 4. Pagination dans ChatDao

### Modifier ChatDao.kt

```kotlin
@Dao
interface ChatDao {
    // Méthodes existantes...

    // NOUVELLES MÉTHODES PAGINÉES

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getPagedMessages(conversationId: Long): PagingSource<Int, ChatMessage>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: Long): Int

    @Query("""
        SELECT * FROM conversations
        ORDER BY lastUpdatedAt DESC
    """)
    fun getPagedConversations(): PagingSource<Int, Conversation>

    // Mise à jour des métadonnées de conversation
    @Query("""
        UPDATE conversations
        SET lastUpdatedAt = :timestamp,
            lastMessagePreview = :preview,
            messageCount = messageCount + 1
        WHERE id = :conversationId
    """)
    suspend fun updateConversationMetadata(
        conversationId: Long,
        timestamp: Long,
        preview: String
    )

    @Query("UPDATE conversations SET title = :title WHERE id = :conversationId")
    suspend fun updateConversationTitle(conversationId: Long, title: String)
}
```

---

## 5. Repository avec Pagination

### Modifier ChatRepository.kt

```kotlin
package com.nikolaspaci.app.llamallmlocal.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.nikolaspaci.app.llamallmlocal.data.database.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    // Méthodes existantes...

    // NOUVELLES MÉTHODES PAGINÉES

    fun getPagedMessages(conversationId: Long): Flow<PagingData<ChatMessage>> {
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                enablePlaceholders = false,
                prefetchDistance = 10,
                initialLoadSize = 50
            ),
            pagingSourceFactory = { chatDao.getPagedMessages(conversationId) }
        ).flow
    }

    fun getPagedConversations(): Flow<PagingData<Conversation>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                prefetchDistance = 5
            ),
            pagingSourceFactory = { chatDao.getPagedConversations() }
        ).flow
    }

    suspend fun addMessageToConversation(message: ChatMessage): Long {
        val messageId = chatDao.insertChatMessage(message)

        // Mettre à jour les métadonnées de la conversation
        val preview = message.message.take(100)
        chatDao.updateConversationMetadata(
            conversationId = message.conversationId,
            timestamp = message.timestamp,
            preview = preview
        )

        return messageId
    }

    suspend fun getMessageCount(conversationId: Long): Int {
        return chatDao.getMessageCount(conversationId)
    }
}
```

---

## 6. ViewModel avec Pagination

### Modifier ChatViewModel.kt

```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val engine: ModelEngine,
    private val predictUseCase: PredictUseCase,
    private val parameterProvider: ModelParameterProvider,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val conversationId: Long = savedStateHandle.get<Long>("conversationId") ?: 0L

    // Messages paginés
    val pagedMessages: Flow<PagingData<ChatMessage>> =
        chatRepository.getPagedMessages(conversationId)
            .cachedIn(viewModelScope)

    // ... reste du code
}
```

---

## 7. UI avec LazyPagingItems

### Modifier MessageList.kt

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.viewmodel.Stats

@Composable
fun MessageList(
    pagedMessages: LazyPagingItems<ChatMessage>,
    streamingState: StreamingState?,
    lastMessageStats: Stats?,
    onCancelGeneration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll vers le dernier message
    LaunchedEffect(pagedMessages.itemCount, streamingState?.currentText) {
        val targetIndex = pagedMessages.itemCount + (if (streamingState != null) 1 else 0) - 1
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // État de chargement initial
        if (pagedMessages.loadState.refresh is LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Chargement de messages plus anciens (prepend)
        if (pagedMessages.loadState.prepend is LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        // Messages paginés
        items(
            count = pagedMessages.itemCount,
            key = pagedMessages.itemKey { it.id }
        ) { index ->
            val message = pagedMessages[index]
            if (message != null) {
                val isLastBotMessage = index == pagedMessages.itemCount - 1 &&
                                       message.sender == Sender.BOT &&
                                       streamingState == null

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = if (message.sender == Sender.USER)
                        Arrangement.End else Arrangement.Start
                ) {
                    MessageRow(
                        message = message,
                        stats = if (isLastBotMessage) lastMessageStats else null
                    )
                }
            }
        }

        // Message en streaming
        if (streamingState != null) {
            item(key = "streaming") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    StreamingMessageBubble(
                        text = streamingState.currentText,
                        tokensGenerated = streamingState.tokensGenerated,
                        onCancel = onCancelGeneration
                    )
                }
            }
        }

        // Erreur de chargement
        if (pagedMessages.loadState.refresh is LoadState.Error) {
            item {
                Text(
                    text = "Erreur de chargement",
                    modifier = Modifier.fillMaxWidth(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
```

---

## 8. Génération Automatique des Titres

### Fichier à créer: `data/ConversationTitleGenerator.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.data

object ConversationTitleGenerator {

    private const val MAX_TITLE_LENGTH = 50

    fun generateTitle(firstUserMessage: String): String {
        val cleaned = firstUserMessage
            .replace(Regex("[\\n\\r]+"), " ")  // Supprimer les retours à la ligne
            .replace(Regex("\\s+"), " ")       // Normaliser les espaces
            .trim()

        return if (cleaned.length <= MAX_TITLE_LENGTH) {
            cleaned
        } else {
            // Couper au dernier mot complet
            val truncated = cleaned.take(MAX_TITLE_LENGTH)
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > MAX_TITLE_LENGTH / 2) {
                truncated.take(lastSpace) + "..."
            } else {
                truncated + "..."
            }
        }
    }

    fun generatePreview(message: String, maxLength: Int = 100): String {
        val cleaned = message
            .replace(Regex("[\\n\\r]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (cleaned.length <= maxLength) {
            cleaned
        } else {
            cleaned.take(maxLength - 3) + "..."
        }
    }
}
```

### Utilisation dans HomeViewModel

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    suspend fun startNewConversation(
        modelPath: String,
        firstMessage: String
    ): Long {
        val title = ConversationTitleGenerator.generateTitle(firstMessage)

        val conversation = Conversation(
            modelPath = modelPath,
            title = title,
            lastMessagePreview = ConversationTitleGenerator.generatePreview(firstMessage)
        )
        val conversationId = chatRepository.insertConversation(conversation)

        val chatMessage = ChatMessage(
            conversationId = conversationId,
            sender = Sender.USER,
            message = firstMessage
        )
        chatRepository.addMessageToConversation(chatMessage)

        return conversationId
    }
}
```

---

## 9. Fichiers à Créer/Modifier

### Fichiers à Créer
| Fichier | Description |
|---------|-------------|
| `data/database/DatabaseMigrations.kt` | Migrations Room |
| `data/ConversationTitleGenerator.kt` | Génération de titres |

### Fichiers à Modifier
| Fichier | Modification |
|---------|--------------|
| `libs.versions.toml` | Ajouter Paging 3 |
| `build.gradle.kts` | Ajouter dépendances |
| `data/database/Conversation.kt` | Nouveaux champs |
| `data/database/ChatMessage.kt` | Ajouter index |
| `data/database/AppDatabase.kt` | Version 3 + migrations |
| `data/database/ChatDao.kt` | Requêtes paginées |
| `data/repository/ChatRepository.kt` | Méthodes paginées |
| `viewmodel/ChatViewModel.kt` | Utiliser PagingData |
| `viewmodel/HomeViewModel.kt` | Générer titres |
| `ui/chat/MessageList.kt` | LazyPagingItems |

---

## 10. Séquence d'Implémentation

1. Ajouter dépendances Paging 3
2. Créer `DatabaseMigrations.kt`
3. Modifier `Conversation.kt` et `ChatMessage.kt`
4. Modifier `AppDatabase.kt` (version 3)
5. Modifier `ChatDao.kt` avec requêtes paginées
6. Modifier `ChatRepository.kt`
7. Créer `ConversationTitleGenerator.kt`
8. Modifier `ChatViewModel.kt`
9. Modifier `MessageList.kt` pour LazyPagingItems
10. Tester la pagination
