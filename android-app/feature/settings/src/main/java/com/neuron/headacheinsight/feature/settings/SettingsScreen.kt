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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.model.AppSettings
import com.neuron.headacheinsight.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : androidx.lifecycle.ViewModel() {
    val state: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun save(cloudEnabled: Boolean, backendUrl: String, languageTag: String) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(
                    cloudAnalysisEnabled = cloudEnabled,
                    backendBaseUrl = backendUrl,
                    languageTag = languageTag,
                    languageSelectionCompleted = true,
                    lastSeedVersion = if (it.languageTag == languageTag) it.lastSeedVersion else null,
                )
            }
        }
    }
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(settings = state, onSave = viewModel::save)
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSave: (Boolean, String, String) -> Unit,
) {
    var cloudState by remember(settings.cloudAnalysisEnabled) { mutableStateOf(settings.cloudAnalysisEnabled) }
    var backendState by remember(settings.backendBaseUrl) { mutableStateOf(settings.backendBaseUrl) }
    var languageState by remember(settings.languageTag) { mutableStateOf(settings.languageTag) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightSectionCard(title = stringResource(R.string.settings_cloud_title)) {
            Switch(checked = cloudState, onCheckedChange = { cloudState = it })
        }
        HeadacheInsightSectionCard(title = stringResource(R.string.settings_backend_title)) {
            OutlinedTextField(
                value = backendState,
                onValueChange = { backendState = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_backend_label)) },
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
            onClick = { onSave(cloudState, backendState, languageState) },
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
