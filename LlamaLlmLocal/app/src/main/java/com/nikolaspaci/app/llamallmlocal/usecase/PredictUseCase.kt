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
