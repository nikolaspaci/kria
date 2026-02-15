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
