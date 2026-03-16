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
import androidx.compose.material3.Switch
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
import com.neuron.headacheinsight.core.model.AppSettings
import com.neuron.headacheinsight.core.model.CloudCredentials
import com.neuron.headacheinsight.domain.CloudCredentialsRepository
import com.neuron.headacheinsight.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appSettings: AppSettings = AppSettings(),
    val cloudCredentials: CloudCredentials = CloudCredentials(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
) : androidx.lifecycle.ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        cloudCredentialsRepository.observeCredentials(),
    ) { settings, cloudCredentials ->
        SettingsUiState(
            appSettings = settings,
            cloudCredentials = cloudCredentials,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun save(
        cloudEnabled: Boolean,
        backendUrl: String,
        languageTag: String,
        apiKey: String,
        analysisModel: String,
        questionModel: String,
        transcribeModel: String,
    ) {
        viewModelScope.launch {
            val normalizedBackendUrl = backendUrl.trim().ifBlank { DEFAULT_BACKEND_URL }
            cloudCredentialsRepository.saveCredentials(
                CloudCredentials(
                    apiKey = apiKey.trim(),
                    analysisModel = analysisModel.trim(),
                    questionModel = questionModel.trim(),
                    transcribeModel = transcribeModel.trim(),
                ),
            )
            settingsRepository.updateSettings {
                it.copy(
                    cloudAnalysisEnabled = cloudEnabled,
                    backendBaseUrl = normalizedBackendUrl,
                    languageTag = languageTag,
                    languageSelectionCompleted = true,
                    lastSeedVersion = if (it.languageTag == languageTag) it.lastSeedVersion else null,
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8000/"
    }
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, onSave = viewModel::save)
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSave: (Boolean, String, String, String, String, String, String) -> Unit,
) {
    val settings = state.appSettings
    val cloudCredentials = state.cloudCredentials

    var cloudState by remember(settings.cloudAnalysisEnabled) { mutableStateOf(settings.cloudAnalysisEnabled) }
    var backendState by remember(settings.backendBaseUrl) { mutableStateOf(settings.backendBaseUrl) }
    var languageState by remember(settings.languageTag) { mutableStateOf(settings.languageTag) }
    var apiKeyState by remember(cloudCredentials.apiKey) { mutableStateOf(cloudCredentials.apiKey) }
    var analysisModelState by remember(cloudCredentials.analysisModel) { mutableStateOf(cloudCredentials.analysisModel) }
    var questionModelState by remember(cloudCredentials.questionModel) { mutableStateOf(cloudCredentials.questionModel) }
    var transcribeModelState by remember(cloudCredentials.transcribeModel) { mutableStateOf(cloudCredentials.transcribeModel) }
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightSectionCard(
            title = stringResource(R.string.settings_cloud_title),
            supportingText = stringResource(R.string.settings_cloud_subtitle),
        ) {
            Switch(checked = cloudState, onCheckedChange = { cloudState = it })
        }

        HeadacheInsightSectionCard(
            title = stringResource(R.string.settings_backend_title),
            supportingText = stringResource(R.string.settings_backend_subtitle),
        ) {
            OutlinedTextField(
                value = backendState,
                onValueChange = { backendState = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_backend_label)) },
                singleLine = true,
            )
        }

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
            onClick = {
                onSave(
                    cloudState,
                    backendState,
                    languageState,
                    apiKeyState,
                    analysisModelState,
                    questionModelState,
                    transcribeModelState,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.settings_save))
        }
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
