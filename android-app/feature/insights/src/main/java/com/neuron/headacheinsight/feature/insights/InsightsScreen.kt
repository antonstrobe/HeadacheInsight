package com.neuron.headacheinsight.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.neuron.headacheinsight.core.model.InsightSummary
import com.neuron.headacheinsight.core.ui.localizedTriggerLabel
import com.neuron.headacheinsight.domain.InsightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    insightRepository: InsightRepository,
) : androidx.lifecycle.ViewModel() {
    val state: StateFlow<InsightSummary> = insightRepository.observeInsightSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightSummary(0, 0.0, 0, 0, emptyList()))
}

@Composable
fun InsightsRoute(
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsScreen(state)
}

@Composable
fun InsightsScreen(
    summary: InsightSummary,
) {
    val triggerLabels = summary.suspectedTriggers.map { localizedTriggerLabel(it) }
    Column(
        modifier = Modifier
            .fillMaxSize()
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
    }
}
