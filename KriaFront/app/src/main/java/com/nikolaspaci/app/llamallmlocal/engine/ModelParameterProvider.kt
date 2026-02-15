package com.nikolaspaci.app.llamallmlocal.engine

import com.nikolaspaci.app.llamallmlocal.data.database.Model
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelParameterRepository
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelRepository
import com.nikolaspaci.app.llamallmlocal.data.validation.ParameterValidator
import javax.inject.Inject
import javax.inject.Singleton

interface ModelParameterProvider {
    suspend fun ensureModelExists(filePath: String): Model
    suspend fun getDefaultParameters(filePath: String): ModelParameter
    suspend fun getPendingOrDefaultParameters(filePath: String): ModelParameter
    suspend fun savePendingParameters(filePath: String, params: ModelParameter)
    suspend fun getParametersForConversation(conversationId: Long, filePath: String): ModelParameter
    suspend fun ensureConversationParameters(conversationId: Long, filePath: String): Long
    suspend fun saveParameters(params: ModelParameter)
}

@Singleton
class DefaultModelParameterProvider @Inject constructor(
    private val modelRepository: ModelRepository,
    private val modelParameterRepository: ModelParameterRepository,
    private val chatRepository: ChatRepository
) : ModelParameterProvider {

    override suspend fun ensureModelExists(filePath: String): Model {
        val existing = modelRepository.getByFilePath(filePath)
        if (existing != null) return existing

        val defaultParams = ParameterValidator.getDefaultParameters()
        val paramId = modelParameterRepository.insert(defaultParams)

        val model = Model(filePath = filePath, defaultParameterId = paramId)
        val modelId = modelRepository.insert(model)
        return model.copy(id = modelId)
    }

    override suspend fun getDefaultParameters(filePath: String): ModelParameter {
        val model = ensureModelExists(filePath)
        return modelParameterRepository.getById(model.defaultParameterId!!)
            ?: ModelParameter()
    }

    override suspend fun getPendingOrDefaultParameters(filePath: String): ModelParameter {
        val model = ensureModelExists(filePath)
        if (model.pendingParameterId != null) {
            val pending = modelParameterRepository.getById(model.pendingParameterId)
            if (pending != null) return pending
        }
        return modelParameterRepository.getById(model.defaultParameterId!!)
            ?: ModelParameter()
    }

    override suspend fun savePendingParameters(filePath: String, params: ModelParameter) {
        val model = ensureModelExists(filePath)
        if (model.pendingParameterId != null) {
            val toSave = params.copy(id = model.pendingParameterId)
            modelParameterRepository.update(toSave)
        } else {
            val newId = modelParameterRepository.insert(params.copy(id = 0))
            modelRepository.updatePendingParameterId(model.id, newId)
        }
    }

    override suspend fun getParametersForConversation(conversationId: Long, filePath: String): ModelParameter {
        val paramId = chatRepository.getConversationModelParameterId(conversationId)
        if (paramId != null) {
            val params = modelParameterRepository.getById(paramId)
            if (params != null) return params
        }
        val newId = ensureConversationParameters(conversationId, filePath)
        return modelParameterRepository.getById(newId) ?: ModelParameter()
    }

    override suspend fun ensureConversationParameters(conversationId: Long, filePath: String): Long {
        val model = ensureModelExists(filePath)

        // If pending parameters exist, consume them
        if (model.pendingParameterId != null) {
            chatRepository.updateConversationModelParameterId(conversationId, model.pendingParameterId)
            modelRepository.updatePendingParameterId(model.id, null)
            return model.pendingParameterId
        }

        // Otherwise duplicate defaults
        val defaults = modelParameterRepository.getById(model.defaultParameterId!!)
            ?: ModelParameter()
        val newId = modelParameterRepository.duplicate(defaults)
        chatRepository.updateConversationModelParameterId(conversationId, newId)
        return newId
    }

    override suspend fun saveParameters(params: ModelParameter) {
        modelParameterRepository.update(params)
    }
}
