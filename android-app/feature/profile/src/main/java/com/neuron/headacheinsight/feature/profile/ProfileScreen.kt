package com.neuron.headacheinsight.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.neuron.headacheinsight.core.model.UserProfile
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.domain.ProfileRepository
import com.neuron.headacheinsight.domain.UpsertProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val upsertProfileUseCase: UpsertProfileUseCase,
) : androidx.lifecycle.ViewModel() {
    val profile: StateFlow<UserProfile?> = profileRepository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(profile: UserProfile?, displayName: String, cityRegion: String, cloud: Boolean) {
        viewModelScope.launch {
            upsertProfileUseCase(
                existing = profile,
                displayName = displayName.ifBlank { null },
                cityRegion = cityRegion.ifBlank { null },
                cloudEnabled = cloud,
                locationConsent = profile?.locationConsent ?: false,
                attachmentConsent = profile?.attachmentUploadConsent ?: false,
            )
        }
    }
}

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    ProfileScreen(
        profile = profile,
        onSave = { displayName, cityRegion, cloud -> viewModel.save(profile, displayName, cityRegion, cloud) },
        onBack = onBack,
        onHome = onHome,
    )
}

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    onSave: (String, String, Boolean) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var cityRegion by remember(profile?.cityRegion) { mutableStateOf(profile?.cityRegion.orEmpty()) }
    var cloud by remember(profile?.cloudAnalysisEnabled) { mutableStateOf(profile?.cloudAnalysisEnabled ?: false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightSectionCard(title = stringResource(R.string.profile_title)) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.profile_display_name)) },
            )
            OutlinedTextField(
                value = cityRegion,
                onValueChange = { cityRegion = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.profile_city_region)) },
            )
        }
        HeadacheInsightSectionCard(title = stringResource(R.string.profile_cloud_title)) {
            Switch(checked = cloud, onCheckedChange = { cloud = it })
        }
        Button(
            onClick = { onSave(displayName, cityRegion, cloud) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.profile_save))
        }
        BottomMenuActions(
            onBack = onBack,
            onHome = onHome,
        )
    }
}
