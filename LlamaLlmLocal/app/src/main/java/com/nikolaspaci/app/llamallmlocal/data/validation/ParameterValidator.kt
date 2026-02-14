package com.nikolaspaci.app.llamallmlocal.data.validation

import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter

object ParameterValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: Map<String, String> = emptyMap()
    )

    fun validate(params: ModelParameter): ValidationResult {
        val errors = mutableMapOf<String, String>()

        if (params.temperature !in ModelParameter.TEMPERATURE_RANGE) {
            errors["temperature"] = "Doit être entre ${ModelParameter.TEMPERATURE_RANGE.start} et ${ModelParameter.TEMPERATURE_RANGE.endInclusive}"
        }

        if (params.topK !in ModelParameter.TOP_K_RANGE) {
            errors["topK"] = "Doit être entre ${ModelParameter.TOP_K_RANGE.first} et ${ModelParameter.TOP_K_RANGE.last}"
        }

        if (params.topP !in ModelParameter.TOP_P_RANGE) {
            errors["topP"] = "Doit être entre ${ModelParameter.TOP_P_RANGE.start} et ${ModelParameter.TOP_P_RANGE.endInclusive}"
        }

        if (params.minP !in ModelParameter.MIN_P_RANGE) {
            errors["minP"] = "Doit être entre ${ModelParameter.MIN_P_RANGE.start} et ${ModelParameter.MIN_P_RANGE.endInclusive}"
        }

        if (params.contextSize !in ModelParameter.CONTEXT_SIZE_VALUES) {
            errors["contextSize"] = "Valeurs autorisées: ${ModelParameter.CONTEXT_SIZE_VALUES.joinToString()}"
        }

        if (params.maxTokens !in ModelParameter.MAX_TOKENS_RANGE) {
            errors["maxTokens"] = "Doit être entre ${ModelParameter.MAX_TOKENS_RANGE.first} et ${ModelParameter.MAX_TOKENS_RANGE.last}"
        }

        val maxThreads = ModelParameter.getMaxThreads()
        if (params.threadCount !in 1..maxThreads) {
            errors["threadCount"] = "Doit être entre 1 et $maxThreads"
        }

        if (params.repeatPenalty !in ModelParameter.REPEAT_PENALTY_RANGE) {
            errors["repeatPenalty"] = "Doit être entre ${ModelParameter.REPEAT_PENALTY_RANGE.start} et ${ModelParameter.REPEAT_PENALTY_RANGE.endInclusive}"
        }

        return ValidationResult(errors.isEmpty(), errors)
    }

    fun getDefaultParameters(modelId: String): ModelParameter {
        return ModelParameter(
            modelId = modelId,
            threadCount = (ModelParameter.getMaxThreads() * 0.7).toInt().coerceIn(1, ModelParameter.getMaxThreads())
        )
    }
}
