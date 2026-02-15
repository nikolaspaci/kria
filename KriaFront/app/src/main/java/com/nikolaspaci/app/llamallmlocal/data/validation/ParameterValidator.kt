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
            errors["temperature"] = "Must be between ${ModelParameter.TEMPERATURE_RANGE.start} and ${ModelParameter.TEMPERATURE_RANGE.endInclusive}"
        }

        if (params.topK !in ModelParameter.TOP_K_RANGE) {
            errors["topK"] = "Must be between ${ModelParameter.TOP_K_RANGE.first} and ${ModelParameter.TOP_K_RANGE.last}"
        }

        if (params.topP !in ModelParameter.TOP_P_RANGE) {
            errors["topP"] = "Must be between ${ModelParameter.TOP_P_RANGE.start} and ${ModelParameter.TOP_P_RANGE.endInclusive}"
        }

        if (params.minP !in ModelParameter.MIN_P_RANGE) {
            errors["minP"] = "Must be between ${ModelParameter.MIN_P_RANGE.start} and ${ModelParameter.MIN_P_RANGE.endInclusive}"
        }

        if (params.contextSize !in ModelParameter.CONTEXT_SIZE_VALUES) {
            errors["contextSize"] = "Allowed values: ${ModelParameter.CONTEXT_SIZE_VALUES.joinToString()}"
        }

        if (params.maxTokens !in ModelParameter.MAX_TOKENS_RANGE) {
            errors["maxTokens"] = "Must be between ${ModelParameter.MAX_TOKENS_RANGE.first} and ${ModelParameter.MAX_TOKENS_RANGE.last}"
        }

        val maxThreads = ModelParameter.getMaxThreads()
        if (params.threadCount !in 1..maxThreads) {
            errors["threadCount"] = "Must be between 1 and $maxThreads"
        }

        if (params.repeatPenalty !in ModelParameter.REPEAT_PENALTY_RANGE) {
            errors["repeatPenalty"] = "Must be between ${ModelParameter.REPEAT_PENALTY_RANGE.start} and ${ModelParameter.REPEAT_PENALTY_RANGE.endInclusive}"
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
