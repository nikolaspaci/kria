package com.nikolaspaci.app.llamallmlocal.engine

import com.nikolaspaci.app.llamallmlocal.LlamaApi
import com.nikolaspaci.app.llamallmlocal.PredictCallback
import com.nikolaspaci.app.llamallmlocal.data.database.ChatMessage
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.jni.PredictionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
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
    private var currentSystemPrompt: String = ""
    private val mutex = Mutex()

    private val _loadState = MutableStateFlow<ModelEngine.LoadState>(ModelEngine.LoadState.Idle)
    override val loadState: StateFlow<ModelEngine.LoadState> = _loadState.asStateFlow()

    override suspend fun loadModel(modelPath: String, parameters: ModelParameter): Result<Unit> {
        return mutex.withLock {
            try {
                val modelName = File(modelPath).nameWithoutExtension
                _loadState.value = ModelEngine.LoadState.Loading(0f, modelName)

                if (sessionPtr != 0L) {
                    withContext(Dispatchers.IO) {
                        LlamaApi.free(sessionPtr)
                    }
                    sessionPtr = 0
                    currentModelPath = null
                }

                withContext(Dispatchers.IO) {
                    sessionPtr = LlamaApi.init(modelPath, parameters)
                }

                if (sessionPtr == 0L) {
                    _loadState.value = ModelEngine.LoadState.Error("Echec du chargement du modele")
                    Result.failure(IllegalStateException("Model loading failed"))
                } else {
                    currentModelPath = modelPath
                    currentSystemPrompt = parameters.systemPrompt
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
            trySend(PredictionEvent.Error("Aucun modele charge", isRecoverable = false))
            close()
            return@callbackFlow
        }

        val callback = object : PredictCallback {
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

    override suspend fun restoreHistory(messages: List<ChatMessage>, systemPrompt: String) {
        if (sessionPtr == 0L) return

        val prompt = systemPrompt.ifEmpty { currentSystemPrompt }
        withContext(Dispatchers.IO) {
            LlamaApi.restoreHistory(sessionPtr, messages.toTypedArray(), prompt)
        }
    }

    override fun isModelLoaded(): Boolean = sessionPtr != 0L

    override fun getCurrentModelPath(): String? = currentModelPath
}
