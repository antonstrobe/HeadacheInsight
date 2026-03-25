package com.neuron.headacheinsight.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusBadge
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusColors
import com.neuron.headacheinsight.core.designsystem.headacheInsightActionButtonColors
import com.neuron.headacheinsight.core.designsystem.preferredHorizontalAlignment
import com.neuron.headacheinsight.core.designsystem.preferredTextAlign
import com.neuron.headacheinsight.core.model.AppSettings
import com.neuron.headacheinsight.core.model.BackendConnectionStatus
import com.neuron.headacheinsight.core.model.CloudCredentials
import com.neuron.headacheinsight.core.model.HandPreference
import com.neuron.headacheinsight.core.model.OpenAiAutoModelId
import com.neuron.headacheinsight.core.model.buildOpenAiModelCatalog
import com.neuron.headacheinsight.core.model.isOpenAiAutoModel
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.ui.SectionActionRow
import com.neuron.headacheinsight.domain.BackendStatusRepository
import com.neuron.headacheinsight.domain.CloudCredentialsRepository
import com.neuron.headacheinsight.domain.OpenAiModelRepository
import com.neuron.headacheinsight.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ConnectionCheckState {
    IDLE,
    TESTING,
    SUCCESS,
    ERROR,
}

enum class ModelCatalogState {
    IDLE,
    LOADING,
    READY,
    ERROR,
}

private data class ConnectionUiState(
    val state: ConnectionCheckState = ConnectionCheckState.IDLE,
    val status: BackendConnectionStatus? = null,
    val errorMessage: String? = null,
)

private data class ModelCatalogUiState(
    val state: ModelCatalogState = ModelCatalogState.IDLE,
    val availableModels: List<String> = emptyList(),
    val errorMessage: String? = null,
)

data class SettingsUiState(
    val appSettings: AppSettings = AppSettings(),
    val cloudCredentials: CloudCredentials = CloudCredentials(),
    val connectionState: ConnectionCheckState = ConnectionCheckState.IDLE,
    val connectionStatus: BackendConnectionStatus? = null,
    val connectionErrorMessage: String? = null,
    val analysisModels: List<String> = emptyList(),
    val questionModels: List<String> = emptyList(),
    val transcribeModels: List<String> = emptyList(),
    val supportedModelCount: Int = 0,
    val modelCatalogState: ModelCatalogState = ModelCatalogState.IDLE,
    val modelCatalogErrorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
    private val backendStatusRepository: BackendStatusRepository,
    private val openAiModelRepository: OpenAiModelRepository,
) : androidx.lifecycle.ViewModel() {
    private val connectionState = MutableStateFlow(ConnectionUiState())
    private val modelCatalogState = MutableStateFlow(ModelCatalogUiState())

    init {
        viewModelScope.launch {
            cloudCredentialsRepository.observeCredentials()
                .map { it.apiKey.trim() }
                .distinctUntilChanged()
                .collectLatest(::loadAvailableModels)
        }
    }

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        cloudCredentialsRepository.observeCredentials(),
        connectionState,
        modelCatalogState,
    ) { settings, cloudCredentials, connection, models ->
        val catalog = buildOpenAiModelCatalog(models.availableModels)
        SettingsUiState(
            appSettings = settings,
            cloudCredentials = cloudCredentials,
            connectionState = connection.state,
            connectionStatus = connection.status,
            connectionErrorMessage = connection.errorMessage,
            analysisModels = catalog.analysisModels,
            questionModels = catalog.questionModels,
            transcribeModels = catalog.transcribeModels,
            supportedModelCount = listOf(
                catalog.analysisModels,
                catalog.questionModels,
                catalog.transcribeModels,
            ).flatten().distinct().size,
            modelCatalogState = models.state,
            modelCatalogErrorMessage = models.errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refreshAvailableModels(apiKey: String) {
        viewModelScope.launch {
            loadAvailableModels(apiKey)
        }
    }

    fun save(
        languageTag: String,
        handPreference: HandPreference,
        apiKey: String,
        analysisModel: String,
        questionModel: String,
        transcribeModel: String,
    ) {
        viewModelScope.launch {
            persistSettings(
                languageTag = languageTag,
                handPreference = handPreference,
                apiKey = apiKey,
                analysisModel = analysisModel,
                questionModel = questionModel,
                transcribeModel = transcribeModel,
            )
        }
    }

    fun saveAndTest(
        languageTag: String,
        handPreference: HandPreference,
        apiKey: String,
        analysisModel: String,
        questionModel: String,
        transcribeModel: String,
    ) {
        viewModelScope.launch {
            persistSettings(
                languageTag = languageTag,
                handPreference = handPreference,
                apiKey = apiKey,
                analysisModel = analysisModel,
                questionModel = questionModel,
                transcribeModel = transcribeModel,
            )
            connectionState.emit(ConnectionUiState(state = ConnectionCheckState.TESTING))
            backendStatusRepository.testConnection().fold(
                onSuccess = { status ->
                    connectionState.emit(
                        ConnectionUiState(
                            state = ConnectionCheckState.SUCCESS,
                            status = status,
                        ),
                    )
                },
                onFailure = { error ->
                    connectionState.emit(
                        ConnectionUiState(
                            state = ConnectionCheckState.ERROR,
                            errorMessage = error.message,
                        ),
                    )
                },
            )
        }
    }

    private suspend fun loadAvailableModels(apiKey: String) {
        val normalizedApiKey = apiKey.trim()
        if (normalizedApiKey.isBlank()) {
            modelCatalogState.emit(ModelCatalogUiState())
            return
        }

        val existingModels = modelCatalogState.value.availableModels
        modelCatalogState.emit(
            ModelCatalogUiState(
                state = ModelCatalogState.LOADING,
                availableModels = existingModels,
            ),
        )
        openAiModelRepository.listModels(normalizedApiKey).fold(
            onSuccess = { models ->
                modelCatalogState.emit(
                    ModelCatalogUiState(
                        state = ModelCatalogState.READY,
                        availableModels = models,
                    ),
                )
            },
            onFailure = { error ->
                modelCatalogState.emit(
                    ModelCatalogUiState(
                        state = ModelCatalogState.ERROR,
                        availableModels = existingModels,
                        errorMessage = error.message,
                    ),
                )
            },
        )
    }

    private suspend fun persistSettings(
        languageTag: String,
        handPreference: HandPreference,
        apiKey: String,
        analysisModel: String,
        questionModel: String,
        transcribeModel: String,
    ) {
        val defaults = CloudCredentials()
        cloudCredentialsRepository.saveCredentials(
            CloudCredentials(
                apiKey = apiKey.trim(),
                analysisModel = analysisModel.trim().ifBlank { defaults.analysisModel },
                questionModel = questionModel.trim().ifBlank { defaults.questionModel },
                transcribeModel = transcribeModel.trim().ifBlank { defaults.transcribeModel },
            ),
        )
        settingsRepository.updateSettings {
            it.copy(
                cloudAnalysisEnabled = true,
                languageTag = languageTag,
                languageSelectionCompleted = true,
                handPreference = handPreference,
                lastSeedVersion = if (it.languageTag == languageTag) it.lastSeedVersion else null,
            )
        }
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onSave = viewModel::save,
        onSaveAndTest = viewModel::saveAndTest,
        onRefreshAvailableModels = viewModel::refreshAvailableModels,
        onBack = onBack,
        onHome = onHome,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSave: (String, HandPreference, String, String, String, String) -> Unit,
    onSaveAndTest: (String, HandPreference, String, String, String, String) -> Unit,
    onRefreshAvailableModels: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val settings = state.appSettings
    val cloudCredentials = state.cloudCredentials

    var languageState by remember(settings.languageTag) { mutableStateOf(settings.languageTag) }
    var handPreferenceState by remember(settings.handPreference) { mutableStateOf(settings.handPreference) }
    var apiKeyState by remember(cloudCredentials.apiKey) { mutableStateOf(cloudCredentials.apiKey) }
    var analysisModelState by remember(cloudCredentials.analysisModel) { mutableStateOf(cloudCredentials.analysisModel) }
    var questionModelState by remember(cloudCredentials.questionModel) { mutableStateOf(cloudCredentials.questionModel) }
    var transcribeModelState by remember(cloudCredentials.transcribeModel) { mutableStateOf(cloudCredentials.transcribeModel) }
    var showApiKey by remember { mutableStateOf(false) }

    fun submitSave() {
        onSave(
            languageState,
            handPreferenceState,
            apiKeyState,
            analysisModelState,
            questionModelState,
            transcribeModelState,
        )
    }

    fun submitSaveAndTest() {
        onSaveAndTest(
            languageState,
            handPreferenceState,
            apiKeyState,
            analysisModelState,
            questionModelState,
            transcribeModelState,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = preferredHorizontalAlignment(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightSectionCard(
            title = stringResource(R.string.settings_openai_title),
            supportingText = stringResource(R.string.settings_openai_subtitle),
        ) {
            OutlinedTextField(
                value = apiKeyState,
                onValueChange = { apiKeyState = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = preferredTextAlign()),
                label = { Text(stringResource(R.string.settings_openai_key_label)) },
                supportingText = { Text(stringResource(R.string.settings_openai_storage_note)) },
                singleLine = true,
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            )
            SectionActionRow {
                TextButton(onClick = { showApiKey = !showApiKey }) {
                    Text(
                        if (showApiKey) {
                            stringResource(R.string.settings_key_hide)
                        } else {
                            stringResource(R.string.settings_key_show)
                        },
                    )
                }
            }
            ConnectionStatusBlock(state = state)
        }

        HeadacheInsightSectionCard(
            title = stringResource(R.string.settings_models_title),
            supportingText = stringResource(R.string.settings_models_subtitle),
        ) {
            ModelCatalogStatusBlock(
                state = state.modelCatalogState,
                availableModelsCount = state.supportedModelCount,
                errorMessage = state.modelCatalogErrorMessage,
            )
            SectionActionRow {
                TextButton(
                    onClick = { onRefreshAvailableModels(apiKeyState) },
                    enabled = apiKeyState.isNotBlank() && state.modelCatalogState != ModelCatalogState.LOADING,
                ) {
                    Text(
                        if (state.modelCatalogState == ModelCatalogState.LOADING) {
                            stringResource(R.string.settings_models_loading)
                        } else {
                            stringResource(R.string.settings_models_refresh)
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_models_auto_note),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = preferredTextAlign(),
            )
            ModelDropdownField(
                label = stringResource(R.string.settings_analysis_model_label),
                selectedModel = analysisModelState,
                availableModels = state.analysisModels,
                onModelSelected = { analysisModelState = it },
            )
            ModelDropdownField(
                label = stringResource(R.string.settings_question_model_label),
                selectedModel = questionModelState,
                availableModels = state.questionModels,
                onModelSelected = { questionModelState = it },
            )
            ModelDropdownField(
                label = stringResource(R.string.settings_transcribe_model_label),
                selectedModel = transcribeModelState,
                availableModels = state.transcribeModels,
                onModelSelected = { transcribeModelState = it },
            )
        }

        HeadacheInsightSectionCard(
            title = stringResource(R.string.settings_language_title),
            supportingText = stringResource(R.string.settings_language_subtitle),
        ) {
            SelectionOptionButton(
                label = stringResource(R.string.settings_language_russian),
                selected = languageState.startsWith("ru", ignoreCase = true),
                onClick = { languageState = "ru-RU" },
            )
            SelectionOptionButton(
                label = stringResource(R.string.settings_language_english),
                selected = languageState.startsWith("en", ignoreCase = true),
                onClick = { languageState = "en-US" },
            )
        }

        HeadacheInsightSectionCard(
            title = stringResource(R.string.settings_hand_preference_title),
            supportingText = stringResource(R.string.settings_hand_preference_subtitle),
        ) {
            SelectionOptionButton(
                label = stringResource(R.string.settings_hand_preference_right),
                selected = handPreferenceState == HandPreference.RIGHT,
                onClick = { handPreferenceState = HandPreference.RIGHT },
            )
            SelectionOptionButton(
                label = stringResource(R.string.settings_hand_preference_left),
                selected = handPreferenceState == HandPreference.LEFT,
                onClick = { handPreferenceState = HandPreference.LEFT },
            )
            SelectionOptionButton(
                label = stringResource(R.string.settings_hand_preference_center),
                selected = handPreferenceState == HandPreference.CENTER,
                onClick = { handPreferenceState = HandPreference.CENTER },
            )
        }

        Button(
            onClick = ::submitSaveAndTest,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.connectionState != ConnectionCheckState.TESTING,
        ) {
            Text(
                if (state.connectionState == ConnectionCheckState.TESTING) {
                    stringResource(R.string.settings_connection_testing)
                } else {
                    stringResource(R.string.settings_save_and_test)
                },
            )
        }
        OutlinedButton(
            onClick = ::submitSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save))
        }
        BottomMenuActions(
            onBack = onBack,
            onHome = onHome,
        )
    }
}

@Composable
private fun ModelCatalogStatusBlock(
    state: ModelCatalogState,
    availableModelsCount: Int,
    errorMessage: String?,
) {
    val badgeColor = when (state) {
        ModelCatalogState.READY -> HeadacheInsightStatusColors.LocalComplete
        ModelCatalogState.ERROR -> HeadacheInsightStatusColors.Warning
        ModelCatalogState.LOADING -> HeadacheInsightStatusColors.CloudAnalyzed
        ModelCatalogState.IDLE -> HeadacheInsightStatusColors.Queued
    }
    val badgeLabel = when (state) {
        ModelCatalogState.READY -> stringResource(R.string.settings_models_ready)
        ModelCatalogState.ERROR -> stringResource(R.string.settings_models_error)
        ModelCatalogState.LOADING -> stringResource(R.string.settings_models_loading)
        ModelCatalogState.IDLE -> stringResource(R.string.settings_models_idle_badge)
    }
    val helperText = when (state) {
        ModelCatalogState.IDLE -> stringResource(R.string.settings_models_idle)
        ModelCatalogState.LOADING -> stringResource(R.string.settings_models_loading_hint)
        ModelCatalogState.READY -> {
            if (availableModelsCount > 0) {
                stringResource(R.string.settings_models_loaded_count, availableModelsCount)
            } else {
                stringResource(R.string.settings_models_empty)
            }
        }
        ModelCatalogState.ERROR -> stringResource(R.string.settings_models_error_hint)
    }

    HeadacheInsightStatusBadge(
        label = badgeLabel,
        color = badgeColor,
    )
    Text(
        text = helperText,
        modifier = Modifier.fillMaxWidth(),
        textAlign = preferredTextAlign(),
    )
    if (state == ModelCatalogState.ERROR && !errorMessage.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.settings_models_error_message, errorMessage),
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.error,
            textAlign = preferredTextAlign(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdownField(
    label: String,
    selectedModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit,
) {
    var expanded by remember(selectedModel, availableModels) { mutableStateOf(false) }
    val options = remember(selectedModel, availableModels) {
        buildModelOptions(
            currentModel = selectedModel,
            availableModels = availableModels,
        )
    }
    val displayModel = if (isOpenAiAutoModel(selectedModel)) {
        stringResource(R.string.settings_models_auto)
    } else {
        selectedModel
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (options.isNotEmpty()) {
                expanded = !expanded
            }
        },
    ) {
        OutlinedTextField(
            value = displayModel,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = preferredTextAlign()),
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.settings_models_placeholder)) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isOpenAiAutoModel(model)) {
                                stringResource(R.string.settings_models_auto)
                            } else {
                                model
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = preferredTextAlign(),
                        )
                    },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun buildModelOptions(
    currentModel: String,
    availableModels: List<String>,
): List<String> = buildList {
    add(OpenAiAutoModelId)
    currentModel.trim().takeIf(String::isNotBlank)?.let(::add)
    addAll(availableModels)
}.map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

@Composable
private fun ConnectionStatusBlock(
    state: SettingsUiState,
) {
    val badgeColor = when (state.connectionState) {
        ConnectionCheckState.SUCCESS -> HeadacheInsightStatusColors.LocalComplete
        ConnectionCheckState.ERROR -> HeadacheInsightStatusColors.Warning
        ConnectionCheckState.TESTING -> HeadacheInsightStatusColors.CloudAnalyzed
        ConnectionCheckState.IDLE -> HeadacheInsightStatusColors.Queued
    }
    val badgeLabel = when (state.connectionState) {
        ConnectionCheckState.SUCCESS -> stringResource(R.string.settings_connection_success)
        ConnectionCheckState.ERROR -> stringResource(R.string.settings_connection_error)
        ConnectionCheckState.TESTING -> stringResource(R.string.settings_connection_testing)
        ConnectionCheckState.IDLE -> stringResource(R.string.settings_connection_idle)
    }
    HeadacheInsightStatusBadge(
        label = badgeLabel,
        color = badgeColor,
    )
    state.connectionStatus?.let { status ->
        Text(
            text = stringResource(R.string.settings_connection_service, status.serviceName),
            modifier = Modifier.fillMaxWidth(),
            textAlign = preferredTextAlign(),
        )
        Text(
            text = stringResource(
                R.string.settings_connection_models,
                status.analysisModel,
                status.questionModel,
                status.transcribeModel,
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = preferredTextAlign(),
        )
        Text(
            if (status.apiKeyPresent) {
                stringResource(R.string.settings_connection_key_present)
            } else {
                stringResource(R.string.settings_connection_key_missing)
            },
            modifier = Modifier.fillMaxWidth(),
            color = if (status.apiKeyPresent) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.outline
            },
            textAlign = preferredTextAlign(),
        )
    }
    state.connectionErrorMessage?.let { message ->
        Text(
            text = stringResource(R.string.settings_connection_error_message, message),
            modifier = Modifier.fillMaxWidth(),
            textAlign = preferredTextAlign(),
        )
    }
}

@Composable
private fun SelectionOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val text = if (selected) {
        stringResource(R.string.settings_language_selected, label)
    } else {
        label
    }
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = headacheInsightActionButtonColors(),
        ) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = preferredTextAlign(),
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = preferredTextAlign(),
            )
        }
    }
}
