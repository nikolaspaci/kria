# Phase 1: Core Engine (MVP)

## Objectif
Stabilisation de l'intégration JNI et gestion robuste du cycle de vie du modèle.

---

## 1. Corrections de Bugs Critiques

### Bug 1: HomeChatScreen.kt (ligne 85)
**Fichier**: `ui/home/HomeChatScreen.kt`

```kotlin
// AVANT (ERREUR)
modelFileViewModel.saveModelPath(it, ModelParameter(modelId = it))

// APRÈS (CORRIGÉ)
modelFileViewModel.saveModelPath(it)
```

### Bug 2: PredictJni.cpp (ligne 190)
**Fichier**: `llamaCpp/src/JNIMethods/PredictJni.cpp`

```cpp
// SUPPRIMER cette ligne (double appel):
env->CallVoidMethod(callback_obj, on_complete_method);
```

### Bug 3: ChatViewModel.kt (ligne 80)
**Fichier**: `viewmodel/ChatViewModel.kt`

```kotlin
// AVANT
llamaJniService.loadModel(modelPath)

// APRÈS
val parameters = modelParameterRepository.getModelParameter(modelPath)
    ?: ModelParameter(modelId = modelPath)
llamaJniService.loadModel(modelPath, parameters)
```

---

## 2. Architecture Core Engine

### 2.1 Interface ModelEngine

**Fichier à créer**: `engine/ModelEngine.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.engine

import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.jni.PredictionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ModelEngine {
    sealed class LoadState {
        object Idle : LoadState()
        data class Loading(val progress: Float, val modelName: String) : LoadState()
        data class Loaded(val modelName: String) : LoadState()
        data class Error(val message: String) : LoadState()
    }

    val loadState: StateFlow<LoadState>

    suspend fun loadModel(modelPath: String, parameters: ModelParameter): Result<Unit>
    suspend fun unloadModel(): Result<Unit>
    fun predict(prompt: String, parameters: ModelParameter): Flow<PredictionEvent>
    suspend fun restoreHistory(messages: List<ChatMessage>)
    fun isModelLoaded(): Boolean
    fun getCurrentModelPath(): String?
}
```

### 2.2 Implémentation LlamaEngine

**Fichier à créer**: `engine/LlamaEngine.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.engine

import com.nikolaspaci.app.llamallmlocal.LlamaApi
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.database.Sender
import com.nikolaspaci.app.llamallmlocal.jni.PredictionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEngine @Inject constructor() : ModelEngine {

    private var sessionPtr: Long = 0
    private var currentModelPath: String? = null
    private val mutex = Mutex()

    private val _loadState = MutableStateFlow<ModelEngine.LoadState>(ModelEngine.LoadState.Idle)
    override val loadState: StateFlow<ModelEngine.LoadState> = _loadState.asStateFlow()

    override suspend fun loadModel(modelPath: String, parameters: ModelParameter): Result<Unit> {
        return mutex.withLock {
            try {
                val modelName = File(modelPath).nameWithoutExtension
                _loadState.value = ModelEngine.LoadState.Loading(0f, modelName)

                // Libérer le modèle précédent
                if (sessionPtr != 0L) {
                    withContext(Dispatchers.IO) {
                        LlamaApi.free(sessionPtr)
                    }
                    sessionPtr = 0
                    currentModelPath = null
                }

                // Charger le nouveau modèle
                withContext(Dispatchers.IO) {
                    sessionPtr = LlamaApi.init(modelPath, parameters)
                }

                if (sessionPtr == 0L) {
                    _loadState.value = ModelEngine.LoadState.Error("Échec du chargement du modèle")
                    Result.failure(IllegalStateException("Model loading failed"))
                } else {
                    currentModelPath = modelPath
                    _loadState.value = ModelEngine.LoadState.Loaded(modelName)
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                _loadState.value = ModelEngine.LoadState.Error(e.message ?: "Erreur inconnue")
                Result.failure(e)
            }
        }
    }

    override suspend fun unloadModel(): Result<Unit> {
        return mutex.withLock {
            try {
                if (sessionPtr != 0L) {
                    withContext(Dispatchers.IO) {
                        LlamaApi.free(sessionPtr)
                    }
                    sessionPtr = 0
                    currentModelPath = null
                }
                _loadState.value = ModelEngine.LoadState.Idle
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun predict(prompt: String, parameters: ModelParameter): Flow<PredictionEvent> = callbackFlow {
        if (sessionPtr == 0L) {
            trySend(PredictionEvent.Error("Aucun modèle chargé", isRecoverable = false))
            close()
            return@callbackFlow
        }

        val callback = object : LlamaApi.PredictCallback {
            override fun onToken(token: String) {
                trySend(PredictionEvent.Token(token))
            }

            override fun onComplete(tokensPerSecond: Double, durationInSeconds: Long) {
                trySend(PredictionEvent.Completion(tokensPerSecond, durationInSeconds))
                close()
            }

            override fun onError(error: String) {
                trySend(PredictionEvent.Error(error, isRecoverable = true))
                close()
            }
        }

        LlamaApi.predict(sessionPtr, prompt, parameters, callback)

        awaitClose { }
    }.flowOn(Dispatchers.IO)

    override suspend fun restoreHistory(messages: List<ChatMessage>) {
        if (sessionPtr == 0L) return

        withContext(Dispatchers.IO) {
            val historyArray = messages.map { message ->
                mapOf(
                    "sender" to message.sender.name,
                    "message" to message.message
                )
            }.toTypedArray()

            LlamaApi.restoreHistory(sessionPtr, historyArray)
        }
    }

    override fun isModelLoaded(): Boolean = sessionPtr != 0L

    override fun getCurrentModelPath(): String? = currentModelPath
}
```

### 2.3 ModelParameterProvider

**Fichier à créer**: `engine/ModelParameterProvider.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.engine

import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelParameterRepository
import javax.inject.Inject
import javax.inject.Singleton

interface ModelParameterProvider {
    suspend fun getParametersForModel(modelId: String): ModelParameter
    suspend fun saveParameters(parameters: ModelParameter)
}

@Singleton
class DefaultModelParameterProvider @Inject constructor(
    private val repository: ModelParameterRepository
) : ModelParameterProvider {

    override suspend fun getParametersForModel(modelId: String): ModelParameter {
        return repository.getModelParameter(modelId) ?: ModelParameter(modelId = modelId)
    }

    override suspend fun saveParameters(parameters: ModelParameter) {
        repository.insert(parameters)
    }
}
```

---

## 3. Configuration Hilt

### 3.1 Application Class

**Fichier à créer**: `LlamaApp.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LlamaApp : Application()
```

### 3.2 Module d'Injection

**Fichier à créer**: `di/AppModule.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.di

import android.content.Context
import android.content.SharedPreferences
import com.nikolaspaci.app.llamallmlocal.data.database.AppDatabase
import com.nikolaspaci.app.llamallmlocal.data.database.ChatDao
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameterDao
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelParameterRepository
import com.nikolaspaci.app.llamallmlocal.engine.DefaultModelParameterProvider
import com.nikolaspaci.app.llamallmlocal.engine.LlamaEngine
import com.nikolaspaci.app.llamallmlocal.engine.ModelEngine
import com.nikolaspaci.app.llamallmlocal.engine.ModelParameterProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideModelParameterDao(database: AppDatabase): ModelParameterDao {
        return database.modelParameterDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(chatDao: ChatDao): ChatRepository {
        return ChatRepository(chatDao)
    }

    @Provides
    @Singleton
    fun provideModelParameterRepository(dao: ModelParameterDao): ModelParameterRepository {
        return ModelParameterRepository(dao)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("llama_prefs", Context.MODE_PRIVATE)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EngineModule {

    @Binds
    @Singleton
    abstract fun bindModelEngine(engine: LlamaEngine): ModelEngine

    @Binds
    @Singleton
    abstract fun bindModelParameterProvider(provider: DefaultModelParameterProvider): ModelParameterProvider
}
```

### 3.3 Modifier MainActivity

```kotlin
@AndroidEntryPoint  // Ajouter cette annotation
class MainActivity : ComponentActivity() {
    // ... reste du code
}
```

### 3.4 Modifier AndroidManifest.xml

```xml
<application
    android:name=".LlamaApp"
    ...>
```

---

## 4. UseCase pour la Prédiction

**Fichier à créer**: `usecase/PredictUseCase.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.usecase

import com.nikolaspaci.app.llamallmlocal.engine.ModelEngine
import com.nikolaspaci.app.llamallmlocal.engine.ModelParameterProvider
import com.nikolaspaci.app.llamallmlocal.jni.PredictionEvent
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PredictUseCase @Inject constructor(
    private val engine: ModelEngine,
    private val parameterProvider: ModelParameterProvider
) {
    suspend operator fun invoke(prompt: String, modelId: String): Flow<PredictionEvent> {
        val parameters = parameterProvider.getParametersForModel(modelId)
        return engine.predict(prompt, parameters)
    }
}
```

---

## 5. Mise à jour des Dépendances

### build.gradle.kts (app)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ... autres dépendances existantes
}
```

### build.gradle.kts (project)

```kotlin
plugins {
    // ...
    id("com.google.dagger.hilt.android") version "2.51" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.21" apply false
}
```

### libs.versions.toml

```toml
[versions]
hilt = "2.51"
hilt-navigation-compose = "1.2.0"
ksp = "2.0.0-1.0.21"

[libraries]
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }

[plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

---

## 6. Fichiers à Créer

| Fichier | Description |
|---------|-------------|
| `engine/ModelEngine.kt` | Interface d'abstraction |
| `engine/LlamaEngine.kt` | Implémentation singleton |
| `engine/ModelParameterProvider.kt` | Fournisseur de paramètres |
| `usecase/PredictUseCase.kt` | UseCase prédiction |
| `di/AppModule.kt` | Module Hilt |
| `LlamaApp.kt` | Application class |

## 7. Fichiers à Modifier

| Fichier | Modification |
|---------|--------------|
| `HomeChatScreen.kt` | Corriger ligne 85 |
| `PredictJni.cpp` | Supprimer ligne 190 |
| `ChatViewModel.kt` | Injecter dépendances |
| `MainActivity.kt` | Ajouter @AndroidEntryPoint |
| `AndroidManifest.xml` | Référencer LlamaApp |
| `build.gradle.kts` | Ajouter Hilt/KSP |
| `libs.versions.toml` | Ajouter versions |

---

## 8. Séquence d'Implémentation

1. Corriger les 3 bugs critiques
2. Configurer Hilt (dépendances, plugins)
3. Créer LlamaApp.kt
4. Créer di/AppModule.kt
5. Créer engine/ModelEngine.kt
6. Créer engine/LlamaEngine.kt
7. Créer engine/ModelParameterProvider.kt
8. Créer usecase/PredictUseCase.kt
9. Migrer les ViewModels vers Hilt
10. Tester le build
