package com.neuron.headacheinsight.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.neuron.headacheinsight.core.model.AppSettings
import com.neuron.headacheinsight.core.model.BackendConnectionStatus
import com.neuron.headacheinsight.core.model.CloudCredentials
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.ui.SectionActionRow
import com.neuron.headacheinsight.domain.BackendStatusRepository
import com.neuron.headacheinsight.domain.CloudCredentialsRepository
import com.neuron.headacheinsight.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

private data class ConnectionUiState(
    val state: ConnectionCheckState = ConnectionCheckState.IDLE,
    val status: BackendConnectionStatus? = null,
    val errorMessage: String? = null,
)

data class SettingsUiState(
    val appSettings: AppSettings = AppSettings(),
    val cloudCredentials: CloudCredentials = CloudCredentials(),
    val connectionState: ConnectionCheckState = ConnectionCheckState.IDLE,
    val connectionStatus: BackendConnectionStatus? = null,
    val connectionErrorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
    private val backendStatusRepository: BackendStatusRepository,
) : androidx.lifecycle.ViewModel() {
    private val connectionState = MutableStateFlow(ConnectionUiState())

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        cloudCredentialsRepository.observeCredentials(),
        connectionState,
    ) { settings, cloudCredentials, connection ->
        SettingsUiState(
            appSettings = settings,
            cloudCredentials = cloudCredentials,
            connectionState = connection.state,
            connectionStatus = connection.status,
            connectionErrorMessage = connection.errorMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun save(
        languageTag: String,
        apiKey: String,
        analysisModel: String,
        questionModel: String,
        transcribeModel: String,
    ) {
        viewModelScope.launch {
            persistSettings(
                languageTag = languageTag,
                apiKey = apiKey,
                analysisModel = analysisModel,
                questionModel = questionModel,
                transcribeModel = transcribeModel,
            )
        }
    }

    fun saveAndTest(
        languageTag: String,
        apiKey: String,
        analysisModel: String,
        questionModel: String,
        transcribeModel: String,
    ) {
        viewModelScope.launch {
            persistSettings(
                languageTag = languageTag,
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

    private suspend fun persistSettings(
        languageTag: String,
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
        onBack = onBack,
        onHome = onHome,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSave: (String, String, String, String, String) -> Unit,
    onSaveAndTest: (String, String, String, String, String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val settings = state.appSettings
    val cloudCredentials = state.cloudCredentials

    var languageState by remember(settings.languageTag) { mutableStateOf(settings.languageTag) }
    var apiKeyState by remember(cloudCredentials.apiKey) { mutableStateOf(cloudCredentials.apiKey) }
    var analysisModelState by remember(cloudCredentials.analysisModel) { mutableStateOf(cloudCredentials.analysisModel) }
    var questionModelState by remember(cloudCredentials.questionModel) { mutableStateOf(cloudCredentials.questionModel) }
    var transcribeModelState by remember(cloudCredentials.transcribeModel) { mutableStateOf(cloudCredentials.transcribeModel) }
    var showApiKey by remember { mutableStateOf(false) }

    fun submitSave() {
        onSave(
            languageState,
            apiKeyState,
            analysisModelState,
            questionModelState,
            transcribeModelState,
        )
    }

    fun submitSaveAndTest() {
        onSaveAndTest(
            languageState,
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
            OutlinedTextField(
                value = analysisModelState,
                onValueChange = { analysisModelState = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_analysis_model_label)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = questionModelState,
                onValueChange = { questionModelState = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_question_model_label)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = transcribeModelState,
                onValueChange = { transcribeModelState = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_transcribe_model_label)) },
                singleLine = true,
            )
        }

        HeadacheInsightSectionCard(
            title = stringResource(R.string.settings_language_title),
            supportingText = stringResource(R.string.settings_language_subtitle),
        ) {
            LanguageOptionButton(
                label = stringResource(R.string.settings_language_russian),
                selected = languageState.startsWith("ru", ignoreCase = true),
                onClick = { languageState = "ru-RU" },
            )
            LanguageOptionButton(
                label = stringResource(R.string.settings_language_english),
                selected = languageState.startsWith("en", ignoreCase = true),
                onClick = { languageState = "en-US" },
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
        Text(stringResource(R.string.settings_connection_service, status.serviceName))
        Text(stringResource(R.string.settings_connection_models, status.analysisModel, status.questionModel, status.transcribeModel))
        Text(
            if (status.apiKeyPresent) {
                stringResource(R.string.settings_connection_key_present)
            } else {
                stringResource(R.string.settings_connection_key_missing)
            },
            color = if (status.apiKeyPresent) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
    }
    state.connectionErrorMessage?.let { message ->
        Text(stringResource(R.string.settings_connection_error_message, message))
    }
}

@Composable
private fun LanguageOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val text = if (selected) {
            stringResource(R.string.settings_language_selected, label)
        } else {
            label
        }
        Text(text = text, color = MaterialTheme.colorScheme.onSurface)
    }
}
