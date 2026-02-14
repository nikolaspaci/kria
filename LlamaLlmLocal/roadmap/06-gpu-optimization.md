# Phase 6: Optimisation GPU/NPU

## Objectif
Implémenter l'accélération matérielle via Vulkan et la détection automatique des capacités.

---

## 1. Configuration CMake pour Vulkan

### Modifier CMakeLists.txt

```cmake
# Ajouter après la configuration existante

if(ANDROID)
    # Option pour activer Vulkan
    option(LLAMA_VULKAN "Enable Vulkan GPU acceleration" ON)

    if(LLAMA_VULKAN)
        # Trouver la bibliothèque Vulkan
        find_library(VULKAN_LIB vulkan)

        if(VULKAN_LIB)
            message(STATUS "Vulkan found: ${VULKAN_LIB}")
            set(GGML_VULKAN ON CACHE BOOL "Enable Vulkan backend" FORCE)

            # Ajouter les définitions
            add_definitions(-DGGML_USE_VULKAN)

            # Lier la bibliothèque
            target_link_libraries(jniLlamaCppWrapper ${VULKAN_LIB})
        else()
            message(WARNING "Vulkan library not found - GPU acceleration disabled")
            set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan backend" FORCE)
        endif()
    endif()
endif()
```

### Modifier build.gradle.kts (app)

```kotlin
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DGGML_VULKAN=ON",
                    "-DLLAMA_VULKAN=ON"
                )
            }
        }
    }
}
```

---

## 2. Méthodes JNI pour Hardware Info

### Fichier à créer: `llamaCpp/src/JNIMethods/HardwareInfoJni.cpp`

```cpp
#include <jni.h>
#include <string>

#ifdef GGML_USE_VULKAN
#include "ggml-vulkan.h"
#endif

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_isVulkanAvailable(
    JNIEnv *env,
    jobject /* this */
) {
#ifdef GGML_USE_VULKAN
    // Vérifier si Vulkan est disponible au runtime
    return ggml_vk_has_vulkan() ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

JNIEXPORT jstring JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getVulkanDeviceInfo(
    JNIEnv *env,
    jobject /* this */
) {
#ifdef GGML_USE_VULKAN
    if (!ggml_vk_has_vulkan()) {
        return env->NewStringUTF("Vulkan not available");
    }

    const char* device_info = ggml_vk_get_device_description(0);
    return env->NewStringUTF(device_info ? device_info : "Unknown GPU");
#else
    return env->NewStringUTF("Vulkan not compiled");
#endif
}

JNIEXPORT jint JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getRecommendedGpuLayers(
    JNIEnv *env,
    jobject /* this */
) {
#ifdef GGML_USE_VULKAN
    if (!ggml_vk_has_vulkan()) {
        return 0;
    }

    // Obtenir la VRAM disponible
    size_t vram = ggml_vk_get_device_memory(0);

    // Estimation: environ 200MB par layer pour un modèle 7B
    // Ajuster selon le modèle
    int recommended_layers = static_cast<int>(vram / (200 * 1024 * 1024));

    return recommended_layers > 0 ? recommended_layers : 0;
#else
    return 0;
#endif
}

JNIEXPORT jlong JNICALL
Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getVulkanVramBytes(
    JNIEnv *env,
    jobject /* this */
) {
#ifdef GGML_USE_VULKAN
    if (!ggml_vk_has_vulkan()) {
        return 0;
    }
    return static_cast<jlong>(ggml_vk_get_device_memory(0));
#else
    return 0;
#endif
}

} // extern "C"
```

### Header: `llamaCpp/include/JNIMethods/HardwareInfoJni.hpp`

```cpp
#ifndef HARDWARE_INFO_JNI_HPP
#define HARDWARE_INFO_JNI_HPP

#include <jni.h>

extern "C" {
    JNIEXPORT jboolean JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_isVulkanAvailable(JNIEnv *, jobject);
    JNIEXPORT jstring JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getVulkanDeviceInfo(JNIEnv *, jobject);
    JNIEXPORT jint JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getRecommendedGpuLayers(JNIEnv *, jobject);
    JNIEXPORT jlong JNICALL Java_com_nikolaspaci_app_llamallmlocal_LlamaApi_getVulkanVramBytes(JNIEnv *, jobject);
}

#endif
```

---

## 3. Modifier LlamaApi.kt

```kotlin
object LlamaApi {
    // Méthodes existantes...

    // Nouvelles méthodes pour hardware info
    external fun isVulkanAvailable(): Boolean
    external fun getVulkanDeviceInfo(): String
    external fun getRecommendedGpuLayers(): Int
    external fun getVulkanVramBytes(): Long
}
```

---

## 4. Détection des Capacités Matérielles

### Fichier à créer: `util/HardwareCapabilities.kt`

```kotlin
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
```

---

## 5. Service de Configuration Optimale

### Fichier à créer: `util/OptimalConfigurationService.kt`

```kotlin
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
    val priority: Int = 0  // Plus élevé = plus important
)

@Singleton
class OptimalConfigurationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hardwareCapabilities: HardwareCapabilities
) {

    fun getOptimalConfiguration(modelId: String): ModelParameter {
        val caps = hardwareCapabilities.detect(context)

        // Threads: 70% des cores disponibles
        val optimalThreads = (caps.cpuCores * 0.7).toInt().coerceIn(1, caps.cpuCores)

        // Context size basé sur RAM
        val optimalContextSize = when {
            caps.availableRamBytes > 6L * 1024 * 1024 * 1024 -> 4096
            caps.availableRamBytes > 4L * 1024 * 1024 * 1024 -> 2048
            caps.availableRamBytes > 2L * 1024 * 1024 * 1024 -> 1024
            else -> 512
        }

        // Max tokens basé sur RAM
        val optimalMaxTokens = when {
            caps.availableRamBytes > 4L * 1024 * 1024 * 1024 -> 512
            else -> 256
        }

        // GPU si Vulkan disponible avec assez de VRAM
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
                title = "Activer l'accélération GPU",
                description = "Votre appareil supporte Vulkan (${caps.gpuName}). " +
                             "L'accélération GPU peut améliorer les performances de 2-3x.",
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
                             "Passer de ${currentConfig.threadCount} à $optimalThreads threads.",
                parameterChange = { it.copy(threadCount = optimalThreads) },
                priority = 5
            ))
        }

        // Suggestion context size basée sur RAM
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

        // Suggestion pour réduire si RAM faible
        if (caps.availableRamBytes < 2L * 1024 * 1024 * 1024 &&
            currentConfig.contextSize > 1024) {
            suggestions.add(OptimizationSuggestion(
                title = "Réduire le contexte",
                description = "RAM disponible faible (${formatBytes(caps.availableRamBytes)}). " +
                             "Réduire le contexte à 1024 pour éviter les crashs.",
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
```

---

## 6. Composant HardwareInfoCard

### Fichier à créer: `ui/settings/HardwareInfoCard.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.util.DeviceCapabilities

@Composable
fun HardwareInfoCard(
    capabilities: DeviceCapabilities,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Configuration Matérielle",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            HorizontalDivider()

            // CPU
            InfoRow(
                label = "CPU",
                value = "${capabilities.cpuCores} cores (${capabilities.cpuArchitecture})"
            )

            // RAM
            InfoRow(
                label = "RAM",
                value = "${formatBytes(capabilities.totalRamBytes)} total, " +
                       "${formatBytes(capabilities.availableRamBytes)} disponible"
            )

            // GPU / Vulkan
            if (capabilities.hasVulkan) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "GPU",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = capabilities.gpuName ?: "Vulkan ${capabilities.vulkanVersion}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (capabilities.gpuVramBytes > 0) {
                            Text(
                                text = "VRAM: ${formatBytes(capabilities.gpuVramBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "GPU disponible",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }

                if (capabilities.recommendedGpuLayers > 0) {
                    Text(
                        text = "Couches GPU recommandées: ${capabilities.recommendedGpuLayers}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Accélération GPU non disponible",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800)
                    )
                }
            }

            // NPU
            if (capabilities.hasNpu) {
                InfoRow(
                    label = "NPU",
                    value = capabilities.npuType ?: "Disponible"
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1) {
        "%.1f GB".format(gb)
    } else {
        val mb = bytes / (1024.0 * 1024.0)
        "%.0f MB".format(mb)
    }
}
```

---

## 7. Composant OptimizationSuggestions

### Fichier à créer: `ui/settings/OptimizationSuggestions.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikolaspaci.app.llamallmlocal.util.OptimizationSuggestion

@Composable
fun OptimizationSuggestions(
    suggestions: List<OptimizationSuggestion>,
    onApplySuggestion: (OptimizationSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Suggestions d'optimisation",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            suggestions.forEach { suggestion ->
                SuggestionItem(
                    suggestion = suggestion,
                    onApply = { onApplySuggestion(suggestion) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: OptimizationSuggestion,
    onApply: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = suggestion.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
        }
        TextButton(onClick = onApply) {
            Text("Appliquer")
        }
    }
}
```

---

## 8. Intégration dans SettingsScreen

### Modifier SettingsScreen.kt

```kotlin
@Composable
fun SettingsScreen(
    modelId: String,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // Ajouter la détection hardware
    val hardwareCapabilities = remember { HardwareCapabilities() }
    val context = LocalContext.current
    val capabilities = remember { hardwareCapabilities.detect(context) }

    // ... reste du code existant

    // Dans SettingsContent, ajouter avant les sections:
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // NOUVEAU: Card info hardware
        HardwareInfoCard(capabilities = capabilities)

        // NOUVEAU: Suggestions d'optimisation
        val suggestions = remember(parameters) {
            OptimalConfigurationService(context, hardwareCapabilities)
                .getSuggestions(parameters)
        }
        OptimizationSuggestions(
            suggestions = suggestions,
            onApplySuggestion = { suggestion ->
                onParameterChange(suggestion.parameterChange(parameters))
            }
        )

        // Sections existantes...
        SettingsSection(title = "Paramètres de Sampling") { ... }
        // ...
    }
}
```

---

## 9. Module Hilt pour Hardware

### Ajouter à AppModule.kt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object HardwareModule {

    @Provides
    @Singleton
    fun provideHardwareCapabilities(): HardwareCapabilities {
        return HardwareCapabilities()
    }

    @Provides
    @Singleton
    fun provideOptimalConfigurationService(
        @ApplicationContext context: Context,
        hardwareCapabilities: HardwareCapabilities
    ): OptimalConfigurationService {
        return OptimalConfigurationService(context, hardwareCapabilities)
    }
}
```

---

## 10. Fichiers à Créer/Modifier

### Fichiers à Créer (C++)
| Fichier | Description |
|---------|-------------|
| `llamaCpp/src/JNIMethods/HardwareInfoJni.cpp` | Méthodes JNI pour hardware |
| `llamaCpp/include/JNIMethods/HardwareInfoJni.hpp` | Header |

### Fichiers à Créer (Kotlin)
| Fichier | Description |
|---------|-------------|
| `util/HardwareCapabilities.kt` | Détection matériel |
| `util/OptimalConfigurationService.kt` | Config optimale |
| `ui/settings/HardwareInfoCard.kt` | Affichage hardware |
| `ui/settings/OptimizationSuggestions.kt` | Suggestions |

### Fichiers à Modifier
| Fichier | Modification |
|---------|--------------|
| `llamaCpp/CMakeLists.txt` | Activer Vulkan |
| `app/build.gradle.kts` | Args CMake |
| `LlamaApi.kt` | Nouvelles méthodes JNI |
| `di/AppModule.kt` | Module hardware |
| `ui/settings/SettingsScreen.kt` | Intégrer hardware info |

---

## 11. Séquence d'Implémentation

1. Modifier `CMakeLists.txt` pour Vulkan
2. Créer `HardwareInfoJni.cpp` et header
3. Ajouter méthodes à `LlamaApi.kt`
4. Créer `HardwareCapabilities.kt`
5. Créer `OptimalConfigurationService.kt`
6. Créer `HardwareInfoCard.kt`
7. Créer `OptimizationSuggestions.kt`
8. Modifier `AppModule.kt`
9. Intégrer dans `SettingsScreen.kt`
10. Compiler et tester sur appareil avec GPU

---

## 12. Tests Recommandés

### Tests Unitaires
- `HardwareCapabilitiesTest`: Mock des valeurs système
- `OptimalConfigurationServiceTest`: Vérifier les suggestions

### Tests d'Intégration
- Tester sur appareil avec Vulkan
- Tester sur appareil sans GPU
- Vérifier les performances avec/sans GPU

### Appareils Cibles
- Pixel 6+ (Google Tensor avec GPU Mali)
- Samsung Galaxy S21+ (Exynos/Snapdragon)
- OnePlus (Snapdragon avec Adreno GPU)
