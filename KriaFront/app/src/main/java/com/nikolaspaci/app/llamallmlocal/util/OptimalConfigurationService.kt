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

    fun getOptimalConfiguration(): ModelParameter {
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
                title = "Enable GPU acceleration",
                description = "Your device supports Vulkan (${caps.gpuName}). " +
                             "GPU acceleration can improve performance by 2-3x.",
                parameterChange = { it.copy(useGpu = true) },
                priority = 10
            ))
        }

        // Suggestion threads
        val optimalThreads = (caps.cpuCores * 0.7).toInt()
        if (currentConfig.threadCount < optimalThreads - 1) {
            suggestions.add(OptimizationSuggestion(
                title = "Increase threads",
                description = "Your device has ${caps.cpuCores} cores. " +
                             "Increase from ${currentConfig.threadCount} to $optimalThreads threads.",
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
                title = "Increase context",
                description = "With ${formatBytes(caps.availableRamBytes)} of available RAM, " +
                             "you can use a context of $recommendedContext tokens.",
                parameterChange = { it.copy(contextSize = recommendedContext) },
                priority = 3
            ))
        }

        // Suggestion to reduce if low RAM
        if (caps.availableRamBytes < 2L * 1024 * 1024 * 1024 &&
            currentConfig.contextSize > 1024) {
            suggestions.add(OptimizationSuggestion(
                title = "Reduce context",
                description = "Low available RAM (${formatBytes(caps.availableRamBytes)}). " +
                             "Reduce context to 1024 to avoid crashes.",
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
