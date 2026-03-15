package com.neuron.headacheinsight.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightPrimaryPainButton
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusBadge
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusColors
import com.neuron.headacheinsight.domain.ObserveHomeDashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHomeDashboardUseCase: ObserveHomeDashboardUseCase,
) : androidx.lifecycle.ViewModel() {
    val state: StateFlow<com.neuron.headacheinsight.core.model.HomeDashboard> =
        observeHomeDashboardUseCase()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                com.neuron.headacheinsight.core.model.HomeDashboard(),
            )
}

@Composable
fun HomeRoute(
    onStartEpisode: () -> Unit,
    onContinueEpisode: (String) -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onAttachments: () -> Unit,
    onReports: () -> Unit,
    onQuestions: () -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onStartEpisode = onStartEpisode,
        onContinueEpisode = onContinueEpisode,
        onHistory = onHistory,
        onProfile = onProfile,
        onAttachments = onAttachments,
        onReports = onReports,
        onQuestions = onQuestions,
        onSettings = onSettings,
        onSync = onSync,
    )
}

@Composable
fun HomeScreen(
    state: com.neuron.headacheinsight.core.model.HomeDashboard,
    onStartEpisode: () -> Unit,
    onContinueEpisode: (String) -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onAttachments: () -> Unit,
    onReports: () -> Unit,
    onQuestions: () -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightPrimaryPainButton(
            label = stringResource(R.string.home_pain_button),
            icon = Icons.Outlined.MedicalServices,
            onClick = onStartEpisode,
        )

        HeadacheInsightSectionCard(
            title = stringResource(R.string.home_status_title),
            supportingText = stringResource(R.string.home_status_subtitle),
        ) {
            HeadacheInsightStatusBadge(
                label = if (state.cloudEnabled) {
                    stringResource(R.string.home_cloud_enabled)
                } else {
                    stringResource(R.string.home_local_only)
                },
                color = if (state.cloudEnabled) {
                    HeadacheInsightStatusColors.CloudAnalyzed
                } else {
                    HeadacheInsightStatusColors.LocalComplete
                },
            )
            HeadacheInsightStatusBadge(
                label = stringResource(R.string.home_queue_count, state.queueCount),
                color = if (state.queueCount == 0) {
                    HeadacheInsightStatusColors.LocalComplete
                } else {
                    HeadacheInsightStatusColors.Queued
                },
            )
            Text(stringResource(R.string.home_monthly_headache_days, state.monthlyHeadacheDays))
        }

        state.pendingEpisode?.let { pending ->
            Button(
                onClick = { onContinueEpisode(pending.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_continue_episode))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeActionButton(stringResource(R.string.home_history), onHistory)
            HomeActionButton(stringResource(R.string.home_profile), onProfile)
            HomeActionButton(stringResource(R.string.home_attachments), onAttachments)
            HomeActionButton(stringResource(R.string.home_reports), onReports)
            HomeActionButton(stringResource(R.string.home_new_questions), onQuestions)
            HomeActionButton(stringResource(R.string.home_settings), onSettings)
            HomeActionButton(stringResource(R.string.home_sync_queue), onSync)
        }
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
