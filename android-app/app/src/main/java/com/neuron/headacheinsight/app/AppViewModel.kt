package com.neuron.headacheinsight.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.model.AppSettings
import com.neuron.headacheinsight.domain.EnsureSeedQuestionsUseCase
import com.neuron.headacheinsight.domain.ProfileRepository
import com.neuron.headacheinsight.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppStartupState(
    val settings: AppSettings = AppSettings(),
    val hasProfile: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    profileRepository: ProfileRepository,
    private val ensureSeedQuestionsUseCase: EnsureSeedQuestionsUseCase,
    private val appUpdateChecker: AppUpdateChecker,
) : ViewModel() {
    private val updateInfo = MutableStateFlow<AppUpdateInfo?>(null)

    val state: StateFlow<AppStartupState> = combine(
        settingsRepository.observeSettings(),
        profileRepository.observeProfile(),
        updateInfo,
    ) { settings, profile, availableUpdate ->
        AppStartupState(
            settings = settings,
            hasProfile = profile != null,
            updateInfo = availableUpdate,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppStartupState())

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collectLatest { settings ->
                ensureSeedQuestionsUseCase(settings)
            }
        }
        viewModelScope.launch {
            settingsRepository.observeSettings()
                .map { it.updateChecksEnabled }
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    updateInfo.value = if (enabled) {
                        appUpdateChecker.checkForUpdate()
                    } else {
                        null
                    }
                }
        }
    }

    fun selectLanguage(languageTag: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.updateSettings {
                it.copy(
                    languageTag = languageTag,
                    languageSelectionCompleted = true,
                    lastSeedVersion = null,
                )
            }
            onComplete()
        }
    }

    fun dismissAppUpdatePrompt() {
        updateInfo.value = null
    }
}
