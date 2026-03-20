package com.neuron.headacheinsight.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.ui.ToggleSectionCard
import com.neuron.headacheinsight.domain.ProfileRepository
import com.neuron.headacheinsight.domain.UpsertProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val displayName: String = "",
    val cityRegion: String = "",
    val cloudEnabled: Boolean = false,
    val locationConsent: Boolean = false,
    val attachmentConsent: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    private val upsertProfileUseCase: UpsertProfileUseCase,
) : androidx.lifecycle.ViewModel() {
    private val draft = MutableStateFlow(OnboardingUiState())

    val state: StateFlow<OnboardingUiState> = combine(
        profileRepository.observeProfile(),
        draft,
    ) { profile, current ->
        current.copy(
            displayName = current.displayName.ifBlank { profile?.displayName.orEmpty() },
            cityRegion = current.cityRegion.ifBlank { profile?.cityRegion.orEmpty() },
            cloudEnabled = current.cloudEnabled || (profile?.cloudAnalysisEnabled == true),
            locationConsent = current.locationConsent || (profile?.locationConsent == true),
            attachmentConsent = current.attachmentConsent || (profile?.attachmentUploadConsent == true),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    fun updateDisplayName(value: String) = draft.tryEmit(state.value.copy(displayName = value))
    fun updateCityRegion(value: String) = draft.tryEmit(state.value.copy(cityRegion = value))
    fun updateCloud(enabled: Boolean) = draft.tryEmit(state.value.copy(cloudEnabled = enabled))
    fun updateLocationConsent(enabled: Boolean) = draft.tryEmit(state.value.copy(locationConsent = enabled))
    fun updateAttachmentConsent(enabled: Boolean) = draft.tryEmit(state.value.copy(attachmentConsent = enabled))

    fun save(onComplete: () -> Unit) {
        viewModelScope.launch {
            upsertProfileUseCase(
                existing = null,
                displayName = state.value.displayName.ifBlank { null },
                cityRegion = state.value.cityRegion.ifBlank { null },
                cloudEnabled = state.value.cloudEnabled,
                locationConsent = state.value.locationConsent,
                attachmentConsent = state.value.attachmentConsent,
            )
            onComplete()
        }
    }
}

@Composable
fun OnboardingRoute(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        onDisplayNameChanged = viewModel::updateDisplayName,
        onCityRegionChanged = viewModel::updateCityRegion,
        onCloudChanged = viewModel::updateCloud,
        onLocationConsentChanged = viewModel::updateLocationConsent,
        onAttachmentConsentChanged = viewModel::updateAttachmentConsent,
        onComplete = { viewModel.save(onComplete) },
    )
}

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onDisplayNameChanged: (String) -> Unit,
    onCityRegionChanged: (String) -> Unit,
    onCloudChanged: (Boolean) -> Unit,
    onLocationConsentChanged: (Boolean) -> Unit,
    onAttachmentConsentChanged: (Boolean) -> Unit,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightSectionCard(
            title = stringResource(R.string.onboarding_privacy_title),
            supportingText = stringResource(R.string.onboarding_privacy_subtitle),
        ) {
            Text(stringResource(R.string.onboarding_privacy_body))
        }

        HeadacheInsightSectionCard(title = stringResource(R.string.onboarding_profile_title)) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = onDisplayNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_name_label)) },
            )
            OutlinedTextField(
                value = state.cityRegion,
                onValueChange = onCityRegionChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_city_label)) },
            )
        }

        ToggleSectionCard(
            title = stringResource(R.string.onboarding_cloud_title),
            checked = state.cloudEnabled,
            onCheckedChange = onCloudChanged,
            supportingText = stringResource(R.string.onboarding_cloud_subtitle),
        )
        ToggleSectionCard(
            title = stringResource(R.string.onboarding_location_title),
            checked = state.locationConsent,
            onCheckedChange = onLocationConsentChanged,
            supportingText = stringResource(R.string.onboarding_location_subtitle),
        )
        ToggleSectionCard(
            title = stringResource(R.string.onboarding_attachment_title),
            checked = state.attachmentConsent,
            onCheckedChange = onAttachmentConsentChanged,
            supportingText = stringResource(R.string.onboarding_attachment_subtitle),
        )

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}
