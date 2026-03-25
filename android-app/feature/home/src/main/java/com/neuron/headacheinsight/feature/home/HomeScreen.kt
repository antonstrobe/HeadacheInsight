package com.neuron.headacheinsight.feature.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightPrimaryPainButton
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusBadge
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusColors
import com.neuron.headacheinsight.core.designsystem.headacheInsightActionButtonColors
import com.neuron.headacheinsight.core.designsystem.preferredHorizontalAlignment
import com.neuron.headacheinsight.core.designsystem.preferredTextAlign
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
    onAnalyzeAll: () -> Unit,
    onContinueEpisode: (String) -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onAttachments: () -> Unit,
    onReports: () -> Unit,
    onQuestions: (String) -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onStartEpisode = onStartEpisode,
        onAnalyzeAll = onAnalyzeAll,
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
    onAnalyzeAll: () -> Unit,
    onContinueEpisode: (String) -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit,
    onAttachments: () -> Unit,
    onReports: () -> Unit,
    onQuestions: (String) -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit,
) {
    val bottomActions = buildList {
        add(HomeActionItem(label = stringResource(R.string.home_history), onClick = onHistory))
        add(HomeActionItem(label = stringResource(R.string.home_profile), onClick = onProfile))
        add(HomeActionItem(label = stringResource(R.string.home_attachments), onClick = onAttachments))
        add(HomeActionItem(label = stringResource(R.string.home_reports), onClick = onReports))
        add(
            HomeActionItem(
                label = stringResource(R.string.home_new_questions),
                onClick = { state.pendingEpisode?.id?.let(onQuestions) },
                enabled = state.pendingEpisode != null,
            ),
        )
        add(HomeActionItem(label = stringResource(R.string.home_settings), onClick = onSettings))
        add(HomeActionItem(label = stringResource(R.string.home_sync_queue), onClick = onSync))
    }

    Scaffold(
        bottomBar = {
            Surface {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = preferredHorizontalAlignment(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onAnalyzeAll,
                            colors = headacheInsightActionButtonColors(),
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.home_analyze_all),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Button(
                            onClick = { state.pendingEpisode?.id?.let(onContinueEpisode) },
                            enabled = state.pendingEpisode != null,
                            colors = headacheInsightActionButtonColors(),
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.home_continue_episode),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    HeadacheInsightPrimaryPainButton(
                        label = stringResource(R.string.home_pain_button),
                        icon = Icons.Outlined.MedicalServices,
                        onClick = onStartEpisode,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = preferredHorizontalAlignment(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                horizontalAlignment = preferredHorizontalAlignment(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_info_title),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = preferredTextAlign(),
                )
                Text(
                    text = stringResource(R.string.home_info_subtitle),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = preferredTextAlign(),
                )
            }

            HeadacheInsightSectionCard(
                title = stringResource(R.string.home_status_title),
                supportingText = stringResource(R.string.home_status_subtitle),
            ) {
                HeadacheInsightStatusBadge(
                    label = stringResource(R.string.home_cloud_enabled),
                    color = HeadacheInsightStatusColors.CloudAnalyzed,
                )
                HeadacheInsightStatusBadge(
                    label = stringResource(R.string.home_queue_count, state.queueCount),
                    color = if (state.queueCount == 0) {
                        HeadacheInsightStatusColors.LocalComplete
                    } else {
                        HeadacheInsightStatusColors.Queued
                    },
                )
                Text(
                    text = stringResource(R.string.home_monthly_headache_days, state.monthlyHeadacheDays),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = preferredTextAlign(),
                )
            }

            HeadacheInsightSectionCard(
                title = stringResource(R.string.home_actions_title),
                supportingText = stringResource(R.string.home_actions_subtitle),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    bottomActions.chunked(2).forEach { rowActions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rowActions.forEach { action ->
                                HomeActionButton(
                                    label = action.label,
                                    onClick = action.onClick,
                                    enabled = action.enabled,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowActions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class HomeActionItem(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
private fun HomeActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = headacheInsightActionButtonColors(),
        modifier = modifier.height(64.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
