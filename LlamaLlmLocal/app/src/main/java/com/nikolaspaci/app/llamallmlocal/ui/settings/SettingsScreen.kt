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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.util.HardwareCapabilities
import com.nikolaspaci.app.llamallmlocal.util.OptimalConfigurationService
import com.nikolaspaci.app.llamallmlocal.viewmodel.SettingsUiState
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
                title = { Text("Model Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(Icons.Default.Refresh, "Reset")
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
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
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
    val context = LocalContext.current
    val hardwareCapabilities = remember { HardwareCapabilities() }
    val capabilities = remember { hardwareCapabilities.detect(context) }
    val configService = remember { OptimalConfigurationService(context, hardwareCapabilities) }
    val suggestions = remember(parameters) { configService.getSuggestions(parameters) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hardware Info
        HardwareInfoCard(capabilities = capabilities)

        // Optimization Suggestions
        OptimizationSuggestions(
            suggestions = suggestions,
            onApplySuggestion = { suggestion ->
                onParameterChange(suggestion.parameterChange(parameters))
            }
        )

        // Section Sampling
        SettingsSection(title = "Sampling Parameters") {
            ParameterSlider(
                label = "Temperature",
                value = parameters.temperature,
                onValueChange = { onParameterChange(parameters.copy(temperature = it)) },
                valueRange = ModelParameter.TEMPERATURE_RANGE,
                description = "Controls creativity (0 = deterministic, 2 = very creative)",
                error = errors["temperature"]
            )

            ParameterIntSlider(
                label = "Top K",
                value = parameters.topK,
                onValueChange = { onParameterChange(parameters.copy(topK = it)) },
                valueRange = ModelParameter.TOP_K_RANGE,
                description = "Number of candidate tokens to consider",
                error = errors["topK"]
            )

            ParameterSlider(
                label = "Top P (Nucleus)",
                value = parameters.topP,
                onValueChange = { onParameterChange(parameters.copy(topP = it)) },
                valueRange = ModelParameter.TOP_P_RANGE,
                description = "Cumulative probability of tokens to consider",
                error = errors["topP"]
            )

            ParameterSlider(
                label = "Min P",
                value = parameters.minP,
                onValueChange = { onParameterChange(parameters.copy(minP = it)) },
                valueRange = ModelParameter.MIN_P_RANGE,
                description = "Minimum probability threshold",
                error = errors["minP"]
            )

            ParameterSlider(
                label = "Repeat Penalty",
                value = parameters.repeatPenalty,
                onValueChange = { onParameterChange(parameters.copy(repeatPenalty = it)) },
                valueRange = ModelParameter.REPEAT_PENALTY_RANGE,
                description = "Penalty to avoid repetitions",
                error = errors["repeatPenalty"]
            )
        }

        // Generation section
        SettingsSection(title = "Generation Parameters") {
            ParameterDropdown(
                label = "Context Size",
                value = parameters.contextSize,
                options = ModelParameter.CONTEXT_SIZE_VALUES,
                onValueChange = { onParameterChange(parameters.copy(contextSize = it)) },
                formatOption = { "$it tokens" },
                description = "Model context memory"
            )

            ParameterIntSlider(
                label = "Max Tokens",
                value = parameters.maxTokens,
                onValueChange = { onParameterChange(parameters.copy(maxTokens = it)) },
                valueRange = ModelParameter.MAX_TOKENS_RANGE,
                description = "Maximum response length",
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
                description = "Number of threads for inference (max: ${ModelParameter.getMaxThreads()})",
                error = errors["threadCount"]
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("GPU Acceleration", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Use Vulkan if available",
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

        // Save button
        Button(
            onClick = onSave,
            enabled = errors.isEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
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
