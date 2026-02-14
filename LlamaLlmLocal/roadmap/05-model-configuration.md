# Phase 5: Configuration du Modèle

## Objectif
Étendre les paramètres configurables et améliorer l'UI de configuration.

---

## 1. Extension de ModelParameter

### Modifier ModelParameter.kt

```kotlin
@Entity(tableName = "model_parameters")
data class ModelParameter(
    @PrimaryKey val modelId: String,
    // Paramètres de sampling
    val temperature: Float = 0.8f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float = 0.05f,
    // Nouveaux paramètres
    val contextSize: Int = 2048,
    val maxTokens: Int = 256,
    val threadCount: Int = 4,
    val repeatPenalty: Float = 1.1f,
    val useGpu: Boolean = false
) {
    companion object {
        // Limites de validation
        val TEMPERATURE_RANGE = 0f..2f
        val TOP_K_RANGE = 1..100
        val TOP_P_RANGE = 0f..1f
        val MIN_P_RANGE = 0f..1f
        val CONTEXT_SIZE_VALUES = listOf(512, 1024, 2048, 4096, 8192)
        val MAX_TOKENS_RANGE = 64..2048
        val REPEAT_PENALTY_RANGE = 1f..2f

        fun getMaxThreads(): Int = Runtime.getRuntime().availableProcessors()
    }
}
```

---

## 2. Migration de Base de Données

### Ajouter à DatabaseMigrations.kt

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE model_parameters ADD COLUMN contextSize INTEGER NOT NULL DEFAULT 2048")
        db.execSQL("ALTER TABLE model_parameters ADD COLUMN maxTokens INTEGER NOT NULL DEFAULT 256")
        db.execSQL("ALTER TABLE model_parameters ADD COLUMN threadCount INTEGER NOT NULL DEFAULT 4")
        db.execSQL("ALTER TABLE model_parameters ADD COLUMN repeatPenalty REAL NOT NULL DEFAULT 1.1")
        db.execSQL("ALTER TABLE model_parameters ADD COLUMN useGpu INTEGER NOT NULL DEFAULT 0")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3, MIGRATION_3_4)
```

### Modifier AppDatabase.kt

```kotlin
@Database(
    entities = [Conversation::class, ChatMessage::class, ModelParameter::class],
    version = 4,  // Incrémenté
    exportSchema = true
)
```

---

## 3. Validateur de Paramètres

### Fichier à créer: `data/validation/ParameterValidator.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.data.validation

import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter

object ParameterValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: Map<String, String> = emptyMap()
    )

    fun validate(params: ModelParameter): ValidationResult {
        val errors = mutableMapOf<String, String>()

        // Temperature
        if (params.temperature !in ModelParameter.TEMPERATURE_RANGE) {
            errors["temperature"] = "Doit être entre ${ModelParameter.TEMPERATURE_RANGE.start} et ${ModelParameter.TEMPERATURE_RANGE.endInclusive}"
        }

        // TopK
        if (params.topK !in ModelParameter.TOP_K_RANGE) {
            errors["topK"] = "Doit être entre ${ModelParameter.TOP_K_RANGE.first} et ${ModelParameter.TOP_K_RANGE.last}"
        }

        // TopP
        if (params.topP !in ModelParameter.TOP_P_RANGE) {
            errors["topP"] = "Doit être entre ${ModelParameter.TOP_P_RANGE.start} et ${ModelParameter.TOP_P_RANGE.endInclusive}"
        }

        // MinP
        if (params.minP !in ModelParameter.MIN_P_RANGE) {
            errors["minP"] = "Doit être entre ${ModelParameter.MIN_P_RANGE.start} et ${ModelParameter.MIN_P_RANGE.endInclusive}"
        }

        // Context Size
        if (params.contextSize !in ModelParameter.CONTEXT_SIZE_VALUES) {
            errors["contextSize"] = "Valeurs autorisées: ${ModelParameter.CONTEXT_SIZE_VALUES.joinToString()}"
        }

        // Max Tokens
        if (params.maxTokens !in ModelParameter.MAX_TOKENS_RANGE) {
            errors["maxTokens"] = "Doit être entre ${ModelParameter.MAX_TOKENS_RANGE.first} et ${ModelParameter.MAX_TOKENS_RANGE.last}"
        }

        // Thread Count
        val maxThreads = ModelParameter.getMaxThreads()
        if (params.threadCount !in 1..maxThreads) {
            errors["threadCount"] = "Doit être entre 1 et $maxThreads"
        }

        // Repeat Penalty
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
```

---

## 4. Composant ParameterSlider

### Fichier à créer: `ui/settings/ParameterSlider.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ParameterSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    formatValue: (Float) -> String = { "%.2f".format(it) },
    description: String? = null,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = if (error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
            )
        )

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun ParameterIntSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    description: String? = null,
    error: String? = null,
    modifier: Modifier = Modifier
) {
    ParameterSlider(
        label = label,
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
        steps = valueRange.last - valueRange.first - 1,
        formatValue = { it.toInt().toString() },
        description = description,
        error = error,
        modifier = modifier
    )
}
```

---

## 5. Composant ParameterDropdown

### Fichier à créer: `ui/settings/ParameterDropdown.kt`

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ParameterDropdown(
    label: String,
    value: T,
    options: List<T>,
    onValueChange: (T) -> Unit,
    formatOption: (T) -> String = { it.toString() },
    description: String? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = formatOption(value),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(formatOption(option)) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }

        description?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

---

## 6. Refactoring SettingsScreen

### Modifier SettingsScreen.kt

```kotlin
package com.nikolaspaci.app.llamallmlocal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modelId: String,
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(modelId) {
        viewModel.loadParameters(modelId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres du modèle") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(Icons.Default.Refresh, "Réinitialiser")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is SettingsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is SettingsUiState.Ready -> {
                SettingsContent(
                    parameters = state.parameters,
                    errors = state.validationErrors,
                    onParameterChange = { viewModel.updateParameter(it) },
                    onSave = { viewModel.saveParameters() },
                    modifier = Modifier.padding(padding)
                )
            }

            is SettingsUiState.Saved -> {
                LaunchedEffect(Unit) {
                    onNavigateBack()
                }
            }

            is SettingsUiState.Error -> {
                // Afficher erreur
            }
        }
    }
}

@Composable
private fun SettingsContent(
    parameters: ModelParameter,
    errors: Map<String, String>,
    onParameterChange: (ModelParameter) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Section Sampling
        SettingsSection(title = "Paramètres de Sampling") {
            ParameterSlider(
                label = "Temperature",
                value = parameters.temperature,
                onValueChange = { onParameterChange(parameters.copy(temperature = it)) },
                valueRange = ModelParameter.TEMPERATURE_RANGE,
                description = "Contrôle la créativité (0 = déterministe, 2 = très créatif)",
                error = errors["temperature"]
            )

            ParameterIntSlider(
                label = "Top K",
                value = parameters.topK,
                onValueChange = { onParameterChange(parameters.copy(topK = it)) },
                valueRange = ModelParameter.TOP_K_RANGE,
                description = "Nombre de tokens candidats à considérer",
                error = errors["topK"]
            )

            ParameterSlider(
                label = "Top P (Nucleus)",
                value = parameters.topP,
                onValueChange = { onParameterChange(parameters.copy(topP = it)) },
                valueRange = ModelParameter.TOP_P_RANGE,
                description = "Probabilité cumulative des tokens à considérer",
                error = errors["topP"]
            )

            ParameterSlider(
                label = "Min P",
                value = parameters.minP,
                onValueChange = { onParameterChange(parameters.copy(minP = it)) },
                valueRange = ModelParameter.MIN_P_RANGE,
                description = "Seuil minimum de probabilité",
                error = errors["minP"]
            )

            ParameterSlider(
                label = "Repeat Penalty",
                value = parameters.repeatPenalty,
                onValueChange = { onParameterChange(parameters.copy(repeatPenalty = it)) },
                valueRange = ModelParameter.REPEAT_PENALTY_RANGE,
                description = "Pénalité pour éviter les répétitions",
                error = errors["repeatPenalty"]
            )
        }

        // Section Génération
        SettingsSection(title = "Paramètres de Génération") {
            ParameterDropdown(
                label = "Taille du Contexte",
                value = parameters.contextSize,
                options = ModelParameter.CONTEXT_SIZE_VALUES,
                onValueChange = { onParameterChange(parameters.copy(contextSize = it)) },
                formatOption = { "$it tokens" },
                description = "Mémoire de contexte du modèle"
            )

            ParameterIntSlider(
                label = "Tokens Maximum",
                value = parameters.maxTokens,
                onValueChange = { onParameterChange(parameters.copy(maxTokens = it)) },
                valueRange = ModelParameter.MAX_TOKENS_RANGE,
                description = "Longueur maximale de la réponse",
                error = errors["maxTokens"]
            )
        }

        // Section Performance
        SettingsSection(title = "Performance") {
            ParameterIntSlider(
                label = "Threads CPU",
                value = parameters.threadCount,
                onValueChange = { onParameterChange(parameters.copy(threadCount = it)) },
                valueRange = 1..ModelParameter.getMaxThreads(),
                description = "Nombre de threads pour l'inférence (max: ${ModelParameter.getMaxThreads()})",
                error = errors["threadCount"]
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Accélération GPU", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Utiliser Vulkan si disponible",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = parameters.useGpu,
                    onCheckedChange = { onParameterChange(parameters.copy(useGpu = it)) }
                )
            }
        }

        // Bouton Sauvegarder
        Button(
            onClick = onSave,
            enabled = errors.isEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sauvegarder")
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider()
        content()
    }
}
```

---

## 7. SettingsViewModel

### Modifier SettingsViewModel.kt

```kotlin
package com.nikolaspaci.app.llamallmlocal.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelParameterRepository
import com.nikolaspaci.app.llamallmlocal.data.validation.ParameterValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SettingsUiState {
    object Loading : SettingsUiState()
    data class Ready(
        val parameters: ModelParameter,
        val validationErrors: Map<String, String> = emptyMap()
    ) : SettingsUiState()
    object Saved : SettingsUiState()
    data class Error(val message: String) : SettingsUiState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ModelParameterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var modelId: String = ""

    fun loadParameters(modelId: String) {
        this.modelId = modelId
        viewModelScope.launch {
            val params = repository.getModelParameter(modelId)
                ?: ParameterValidator.getDefaultParameters(modelId)
            _uiState.value = SettingsUiState.Ready(params)
        }
    }

    fun updateParameter(params: ModelParameter) {
        val validation = ParameterValidator.validate(params)
        _uiState.value = SettingsUiState.Ready(params, validation.errors)
    }

    fun saveParameters() {
        val state = _uiState.value
        if (state is SettingsUiState.Ready && state.validationErrors.isEmpty()) {
            viewModelScope.launch {
                repository.insert(state.parameters)
                _uiState.value = SettingsUiState.Saved
            }
        }
    }

    fun resetToDefaults() {
        val defaults = ParameterValidator.getDefaultParameters(modelId)
        _uiState.value = SettingsUiState.Ready(defaults)
    }
}
```

---

## 8. Modifications C++ pour les Nouveaux Paramètres

### Modifier InitJni.cpp

```cpp
// Ajouter après les champs existants
jfieldID contextSizeField = env->GetFieldID(modelParamsClass, "contextSize", "I");
jfieldID threadCountField = env->GetFieldID(modelParamsClass, "threadCount", "I");
jfieldID useGpuField = env->GetFieldID(modelParamsClass, "useGpu", "Z");

jint contextSize = env->GetIntField(modelParameters, contextSizeField);
jint threadCount = env->GetIntField(modelParameters, threadCountField);
jboolean useGpu = env->GetBooleanField(modelParameters, useGpuField);

// Utiliser les valeurs dynamiques
ctx_params.n_ctx = contextSize;
ctx_params.n_threads = threadCount;
ctx_params.n_threads_batch = threadCount;

if (useGpu) {
    model_params.n_gpu_layers = 99;  // Toutes les couches sur GPU
}
```

### Modifier PredictJni.cpp

```cpp
// Ajouter les champs
jfieldID maxTokensField = env->GetFieldID(modelParamsClass, "maxTokens", "I");
jfieldID repeatPenaltyField = env->GetFieldID(modelParamsClass, "repeatPenalty", "F");

jint maxTokens = env->GetIntField(modelParameters, maxTokensField);
jfloat repeatPenalty = env->GetFloatField(modelParameters, repeatPenaltyField);

// Remplacer la constante
// const int max_new_tokens = 256;  // ANCIEN
const int max_new_tokens = maxTokens;  // NOUVEAU

// Ajouter au sampler
session->sparams.penalty_repeat = repeatPenalty;
```

---

## 9. Fichiers à Créer/Modifier

### Fichiers à Créer
| Fichier | Description |
|---------|-------------|
| `data/validation/ParameterValidator.kt` | Validation des paramètres |
| `ui/settings/ParameterSlider.kt` | Composant slider |
| `ui/settings/ParameterDropdown.kt` | Composant dropdown |

### Fichiers à Modifier
| Fichier | Modification |
|---------|--------------|
| `data/database/ModelParameter.kt` | Nouveaux champs |
| `data/database/DatabaseMigrations.kt` | Migration 3→4 |
| `data/database/AppDatabase.kt` | Version 4 |
| `ui/settings/SettingsScreen.kt` | Refactoring complet |
| `viewmodel/SettingsViewModel.kt` | Validation |
| `llamaCpp/src/JNIMethods/InitJni.cpp` | Lire nouveaux params |
| `llamaCpp/src/JNIMethods/PredictJni.cpp` | maxTokens, repeatPenalty |

---

## 10. Séquence d'Implémentation

1. Modifier `ModelParameter.kt` avec les nouveaux champs
2. Créer la migration 3→4
3. Mettre à jour `AppDatabase.kt`
4. Créer `ParameterValidator.kt`
5. Créer `ParameterSlider.kt` et `ParameterDropdown.kt`
6. Refactorer `SettingsViewModel.kt`
7. Refactorer `SettingsScreen.kt`
8. Modifier `InitJni.cpp`
9. Modifier `PredictJni.cpp`
10. Tester la validation et la persistance
