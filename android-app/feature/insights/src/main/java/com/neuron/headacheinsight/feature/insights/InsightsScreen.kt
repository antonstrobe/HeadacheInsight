package com.neuron.headacheinsight.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.neuron.headacheinsight.core.model.AnalysisSnapshot
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.InsightSummary
import com.neuron.headacheinsight.core.model.OwnerType
import com.neuron.headacheinsight.core.ui.AnalysisSnapshotCard
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.ui.EpisodeTimelineList
import com.neuron.headacheinsight.core.ui.localizedTriggerLabel
import com.neuron.headacheinsight.domain.AnalysisRepository
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.InsightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    insightRepository: InsightRepository,
    episodeRepository: EpisodeRepository,
    analysisRepository: AnalysisRepository,
) : androidx.lifecycle.ViewModel() {
    val state: StateFlow<InsightSummary> = insightRepository.observeInsightSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightSummary(0, 0.0, 0, 0, emptyList()))

    val episodes: StateFlow<List<Episode>> = episodeRepository.observeEpisodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val analyses: StateFlow<List<AnalysisSnapshot>> = analysisRepository.observeAllAnalysisHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun InsightsRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val analyses by viewModel.analyses.collectAsStateWithLifecycle()
    InsightsScreen(
        summary = state,
        episodes = episodes,
        analyses = analyses,
        onOpenEpisode = onOpenEpisode,
        onBack = onBack,
        onHome = onHome,
    )
}

@Composable
fun InsightsScreen(
    summary: InsightSummary,
    episodes: List<Episode>,
    analyses: List<AnalysisSnapshot>,
    onOpenEpisode: (String) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val triggerLabels = summary.suspectedTriggers.map { localizedTriggerLabel(it) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightSectionCard(title = stringResource(R.string.insights_headache_days_title)) {
            Text(summary.monthlyHeadacheDays.toString())
        }
        HeadacheInsightSectionCard(title = stringResource(R.string.insights_severity_title)) {
            Text(stringResource(R.string.insights_average_severity, summary.averageSeverity))
        }
        HeadacheInsightSectionCard(title = stringResource(R.string.insights_medication_days_title)) {
            Text(summary.medicationDays.toString())
        }
        HeadacheInsightSectionCard(title = stringResource(R.string.insights_functional_impact_title)) {
            Text(summary.functionalImpactDays.toString())
        }
        HeadacheInsightSectionCard(title = stringResource(R.string.insights_triggers_title)) {
            Text(triggerLabels.joinToString())
        }
        HeadacheInsightSectionCard(
            title = stringResource(R.string.insights_analysis_title),
            supportingText = stringResource(R.string.insights_analysis_subtitle),
        ) {
            if (analyses.isEmpty()) {
                Text(stringResource(R.string.insights_analysis_empty))
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    analyses.forEach { analysis ->
                        AnalysisSnapshotCard(
                            snapshot = analysis,
                            onOpenEpisode = if (analysis.ownerType == OwnerType.EPISODE) {
                                { onOpenEpisode(analysis.ownerId) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        HeadacheInsightSectionCard(
            title = stringResource(R.string.insights_history_title),
            supportingText = stringResource(R.string.insights_history_subtitle),
        ) {
            EpisodeTimelineList(
                episodes = episodes,
                onOpenEpisode = onOpenEpisode,
            )
        }
        BottomMenuActions(
            onBack = onBack,
            onHome = onHome,
        )
    }
}
