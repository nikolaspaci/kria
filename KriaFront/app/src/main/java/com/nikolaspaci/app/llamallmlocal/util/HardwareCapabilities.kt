package com.nikolaspaci.app.llamallmlocal.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.os.Build
import com.nikolaspaci.app.llamallmlocal.LlamaApi
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceCapabilities(
    val cpuCores: Int,
    val cpuArchitecture: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val hasVulkan: Boolean,
    val vulkanVersion: String?,
    val gpuName: String?,
    val gpuVramBytes: Long,
    val recommendedGpuLayers: Int,
    val hasNpu: Boolean,
    val npuType: String?
)

@Singleton
class HardwareCapabilities @Inject constructor() {

    fun detect(context: Context): DeviceCapabilities {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuArch = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        // RAM info
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        // Vulkan info
        val hasVulkan = checkVulkanSupport(context)
        val vulkanVersion = if (hasVulkan) getVulkanVersion(context) else null

        // GPU info via JNI
        val isVulkanRuntime = try { LlamaApi.isVulkanAvailable() } catch (e: Exception) { false }
        val gpuName = if (isVulkanRuntime) {
            try { LlamaApi.getVulkanDeviceInfo() } catch (e: Exception) { getGlGpuInfo() }
        } else {
            getGlGpuInfo()
        }
        val gpuVram = if (isVulkanRuntime) {
            try { LlamaApi.getVulkanVramBytes() } catch (e: Exception) { 0L }
        } else { 0L }
        val recommendedLayers = if (isVulkanRuntime) {
            try { LlamaApi.getRecommendedGpuLayers() } catch (e: Exception) { 0 }
        } else { 0 }

        // NPU detection
        val (hasNpu, npuType) = detectNpu()

        return DeviceCapabilities(
            cpuCores = cpuCores,
            cpuArchitecture = cpuArch,
            totalRamBytes = memoryInfo.totalMem,
            availableRamBytes = memoryInfo.availMem,
            hasVulkan = hasVulkan && isVulkanRuntime,
            vulkanVersion = vulkanVersion,
            gpuName = gpuName,
            gpuVramBytes = gpuVram,
            recommendedGpuLayers = recommendedLayers,
            hasNpu = hasNpu,
            npuType = npuType
        )
    }

    private fun checkVulkanSupport(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        } else {
            false
        }
    }

    private fun getVulkanVersion(context: Context): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val pm = context.packageManager
                val featureInfos = pm.systemAvailableFeatures
                val vulkanFeature = featureInfos.find {
                    it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
                }
                vulkanFeature?.let {
                    val version = it.version
                    val major = (version shr 22) and 0x3FF
                    val minor = (version shr 12) and 0x3FF
                    val patch = version and 0xFFF
                    "$major.$minor.$patch"
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getGlGpuInfo(): String? {
        return try {
            GLES20.glGetString(GLES20.GL_RENDERER)
        } catch (e: Exception) {
            null
        }
    }

    private fun detectNpu(): Pair<Boolean, String?> {
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()
        val soc = Build.SOC_MODEL.lowercase()

        return when {
            hardware.contains("kirin") || soc.contains("kirin") ->
                true to "Huawei NPU (Da Vinci)"
            hardware.contains("exynos") || soc.contains("exynos") ->
                true to "Samsung NPU"
            hardware.contains("tensor") || soc.contains("tensor") ->
                true to "Google TPU"
            hardware.contains("snapdragon") || hardware.contains("qcom") ||
            soc.contains("snapdragon") || soc.contains("sm") ->
                true to "Qualcomm Hexagon DSP"
            hardware.contains("dimensity") || hardware.contains("mediatek") ||
            soc.contains("dimensity") || soc.contains("mt") ->
                true to "MediaTek APU"
            else -> false to null
        }
    }
}
