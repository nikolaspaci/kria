package com.nikolaspaci.app.llamallmlocal.engine

import android.app.ActivityManager
import android.content.Context
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.util.HardwareCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PreflightResult(
    val canLoad: Boolean,
    val adjustedParameters: ModelParameter? = null,
    val warnings: List<String> = emptyList(),
    val error: String? = null
)

@Singleton
class ModelLoadGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hardwareCapabilities: HardwareCapabilities
) {

    companion object {
        private const val RAM_MARGIN_BYTES = 500L * 1024 * 1024 // 500 MB
        private const val MIN_VRAM_BYTES = 256L * 1024 * 1024 // 256 MB
        private const val CPU_USAGE_RATIO = 0.8
        private val CONTEXT_SIZES_DESCENDING = listOf(4096, 2048, 1024, 512)
    }

    fun check(modelPath: String, parameters: ModelParameter): PreflightResult {
        val warnings = mutableListOf<String>()

        // 1. Check model file exists and is readable
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            return PreflightResult(canLoad = false, error = "Le fichier modèle n'existe pas: $modelPath")
        }
        if (!modelFile.canRead()) {
            return PreflightResult(canLoad = false, error = "Le fichier modèle n'est pas lisible: $modelPath")
        }

        val caps = hardwareCapabilities.detect(context)
        val modelSizeBytes = modelFile.length()
        var adjustedParams = parameters

        // 2. Check available RAM
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availableRam = memInfo.availMem

        val estimatedRamNeeded = modelSizeBytes + estimateContextRam(parameters.contextSize) + RAM_MARGIN_BYTES

        if (availableRam < estimatedRamNeeded) {
            // Try to reduce contextSize progressively
            var found = false
            for (ctxSize in CONTEXT_SIZES_DESCENDING) {
                if (ctxSize >= parameters.contextSize) continue
                val reducedEstimate = modelSizeBytes + estimateContextRam(ctxSize) + RAM_MARGIN_BYTES
                if (availableRam >= reducedEstimate) {
                    warnings.add("RAM insuffisante pour contextSize=${parameters.contextSize}, réduit à $ctxSize")
                    adjustedParams = adjustedParams.copy(contextSize = ctxSize)
                    found = true
                    break
                }
            }
            if (!found) {
                return PreflightResult(
                    canLoad = false,
                    error = "RAM insuffisante (${formatMB(availableRam)} disponible, ~${formatMB(estimatedRamNeeded)} requis)"
                )
            }
        }

        // 3. Check GPU VRAM
        if (adjustedParams.useGpu && caps.gpuVramBytes < MIN_VRAM_BYTES) {
            warnings.add("VRAM GPU insuffisante (${formatMB(caps.gpuVramBytes)}), GPU désactivé")
            adjustedParams = adjustedParams.copy(useGpu = false, gpuLayers = 0)
        }

        // 4. Clamp threadCount to 80% of cores
        val maxThreads = (caps.cpuCores * CPU_USAGE_RATIO).toInt().coerceAtLeast(1)
        if (adjustedParams.threadCount > maxThreads) {
            warnings.add("threadCount réduit de ${adjustedParams.threadCount} à $maxThreads (${caps.cpuCores} cores)")
            adjustedParams = adjustedParams.copy(threadCount = maxThreads)
        }

        val hasAdjustments = adjustedParams != parameters
        return PreflightResult(
            canLoad = true,
            adjustedParameters = if (hasAdjustments) adjustedParams else null,
            warnings = warnings
        )
    }

    private fun estimateContextRam(contextSize: Int): Long {
        // Rough estimate: ~2MB per 1K context tokens
        return (contextSize / 1024L) * 2L * 1024 * 1024
    }

    private fun formatMB(bytes: Long): String {
        return "${bytes / (1024 * 1024)} MB"
    }
}
