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
import com.neuron.headacheinsight.core.model.AppSettings
import com.neuron.headacheinsight.core.model.CloudCredentials
import com.neuron.headacheinsight.core.model.LocalSpeechPackState
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.ui.SectionActionRow
import com.neuron.headacheinsight.core.ui.ToggleSectionCard
import com.neuron.headacheinsight.domain.CloudCredentialsRepository
import com.neuron.headacheinsight.domain.LocalSpeechPackManager
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
    val localSpeechPack: LocalSpeechPackState = LocalSpeechPackState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
    private val localSpeechPackManager: LocalSpeechPackManager,
) : androidx.lifecycle.ViewModel() {
    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        cloudCredentialsRepository.observeCredentials(),
        localSpeechPackManager.observeState(),
    ) { settings, cloudCredentials, localSpeechPack ->
        SettingsUiState(
            appSettings = settings,
            cloudCredentials = cloudCredentials,
            localSpeechPack = localSpeechPack,
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

    fun refreshLocalSpeech(languageTag: String) {
        viewModelScope.launch {
            localSpeechPackManager.refresh(languageTag)
        }
    }

    fun installLocalSpeech(languageTag: String) {
        viewModelScope.launch {
            localSpeechPackManager.install(languageTag)
        }
    }

    private companion object {
        const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8000/"
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(state.appSettings.languageTag) {
        viewModel.refreshLocalSpeech(state.appSettings.languageTag)
    }
    SettingsScreen(
        state = state,
        onSave = viewModel::save,
        onRefreshLocalSpeech = viewModel::refreshLocalSpeech,
        onInstallLocalSpeech = viewModel::installLocalSpeech,
        onBack = onBack,
        onHome = onHome,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSave: (Boolean, String, String, String, String, String, String) -> Unit,
    onRefreshLocalSpeech: (String) -> Unit,
    onInstallLocalSpeech: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
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
        ToggleSectionCard(
            title = stringResource(R.string.settings_cloud_title),
            checked = cloudState,
            onCheckedChange = { cloudState = it },
            supportingText = stringResource(R.string.settings_cloud_subtitle),
        )

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
            title = stringResource(R.string.settings_local_speech_title),
            supportingText = stringResource(R.string.settings_local_speech_subtitle),
        ) {
            Text(localSpeechStatusLabel(state.localSpeechPack))
            state.localSpeechPack.progressPercent?.let { progress ->
                Text(stringResource(R.string.settings_local_speech_progress, progress))
            }
            OutlinedButton(
                onClick = { onRefreshLocalSpeech(languageState) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.localSpeechPack.isBusy,
            ) {
                Text(stringResource(R.string.settings_local_speech_refresh))
            }
            Button(
                onClick = { onInstallLocalSpeech(languageState) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.localSpeechPack.isBusy && !state.localSpeechPack.isInstalled,
            ) {
                Text(stringResource(R.string.settings_local_speech_install))
            }
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
        BottomMenuActions(
            onBack = onBack,
            onHome = onHome,
        )
    }
}

@Composable
private fun localSpeechStatusLabel(state: LocalSpeechPackState): String = when (state.status) {
    com.neuron.headacheinsight.core.model.LocalSpeechPackStatus.UNKNOWN -> stringResource(R.string.settings_local_speech_status_unknown)
    com.neuron.headacheinsight.core.model.LocalSpeechPackStatus.CHECKING -> stringResource(R.string.settings_local_speech_status_checking)
    com.neuron.headacheinsight.core.model.LocalSpeechPackStatus.READY -> stringResource(R.string.settings_local_speech_status_ready)
    com.neuron.headacheinsight.core.model.LocalSpeechPackStatus.NOT_INSTALLED -> stringResource(R.string.settings_local_speech_status_not_installed)
    com.neuron.headacheinsight.core.model.LocalSpeechPackStatus.INSTALLING -> stringResource(R.string.settings_local_speech_status_installing)
    com.neuron.headacheinsight.core.model.LocalSpeechPackStatus.SCHEDULED -> stringResource(R.string.settings_local_speech_status_scheduled)
    com.neuron.headacheinsight.core.model.LocalSpeechPackStatus.UNSUPPORTED -> stringResource(R.string.settings_local_speech_status_unsupported)
    com.neuron.headacheinsight.core.model.LocalSpeechPackStatus.ERROR -> stringResource(R.string.settings_local_speech_status_error)
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
