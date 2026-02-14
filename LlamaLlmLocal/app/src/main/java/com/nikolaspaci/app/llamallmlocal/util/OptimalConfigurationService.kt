package com.nikolaspaci.app.llamallmlocal.util

import android.content.Context
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class OptimizationSuggestion(
    val title: String,
    val description: String,
    val parameterChange: (ModelParameter) -> ModelParameter,
    val priority: Int = 0
)

@Singleton
class OptimalConfigurationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hardwareCapabilities: HardwareCapabilities
) {

    fun getOptimalConfiguration(modelId: String): ModelParameter {
        val caps = hardwareCapabilities.detect(context)

        val optimalThreads = (caps.cpuCores * 0.7).toInt().coerceIn(1, caps.cpuCores)

        val optimalContextSize = when {
            caps.availableRamBytes > 6L * 1024 * 1024 * 1024 -> 4096
            caps.availableRamBytes > 4L * 1024 * 1024 * 1024 -> 2048
            caps.availableRamBytes > 2L * 1024 * 1024 * 1024 -> 1024
            else -> 512
        }

        val optimalMaxTokens = when {
            caps.availableRamBytes > 4L * 1024 * 1024 * 1024 -> 512
            else -> 256
        }

        val useGpu = caps.hasVulkan && caps.gpuVramBytes > 512 * 1024 * 1024

        return ModelParameter(
            modelId = modelId,
            temperature = 0.7f,
            topK = 40,
            topP = 0.95f,
            minP = 0.05f,
            contextSize = optimalContextSize,
            maxTokens = optimalMaxTokens,
            threadCount = optimalThreads,
            repeatPenalty = 1.1f,
            useGpu = useGpu
        )
    }

    fun getSuggestions(currentConfig: ModelParameter): List<OptimizationSuggestion> {
        val caps = hardwareCapabilities.detect(context)
        val suggestions = mutableListOf<OptimizationSuggestion>()

        // Suggestion GPU
        if (caps.hasVulkan && !currentConfig.useGpu) {
            suggestions.add(OptimizationSuggestion(
                title = "Activer l'acceleration GPU",
                description = "Votre appareil supporte Vulkan (${caps.gpuName}). " +
                             "L'acceleration GPU peut ameliorer les performances de 2-3x.",
                parameterChange = { it.copy(useGpu = true) },
                priority = 10
            ))
        }

        // Suggestion threads
        val optimalThreads = (caps.cpuCores * 0.7).toInt()
        if (currentConfig.threadCount < optimalThreads - 1) {
            suggestions.add(OptimizationSuggestion(
                title = "Augmenter les threads",
                description = "Votre appareil a ${caps.cpuCores} cores. " +
                             "Passer de ${currentConfig.threadCount} a $optimalThreads threads.",
                parameterChange = { it.copy(threadCount = optimalThreads) },
                priority = 5
            ))
        }

        // Suggestion context size based on RAM
        val recommendedContext = when {
            caps.availableRamBytes > 6L * 1024 * 1024 * 1024 -> 4096
            caps.availableRamBytes > 4L * 1024 * 1024 * 1024 -> 2048
            else -> 1024
        }
        if (currentConfig.contextSize < recommendedContext &&
            caps.availableRamBytes > 4L * 1024 * 1024 * 1024) {
            suggestions.add(OptimizationSuggestion(
                title = "Augmenter le contexte",
                description = "Avec ${formatBytes(caps.availableRamBytes)} de RAM disponible, " +
                             "vous pouvez utiliser un contexte de $recommendedContext tokens.",
                parameterChange = { it.copy(contextSize = recommendedContext) },
                priority = 3
            ))
        }

        // Suggestion to reduce if low RAM
        if (caps.availableRamBytes < 2L * 1024 * 1024 * 1024 &&
            currentConfig.contextSize > 1024) {
            suggestions.add(OptimizationSuggestion(
                title = "Reduire le contexte",
                description = "RAM disponible faible (${formatBytes(caps.availableRamBytes)}). " +
                             "Reduire le contexte a 1024 pour eviter les crashs.",
                parameterChange = { it.copy(contextSize = 1024) },
                priority = 15
            ))
        }

        return suggestions.sortedByDescending { it.priority }
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return "%.1f GB".format(gb)
    }
}
