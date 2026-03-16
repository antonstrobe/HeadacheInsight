package com.neuron.headacheinsight.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.model.Episode

@Composable
fun SeveritySlider(
    severity: Int,
    onSeverityChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.severity_now, severity),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = severity.toFloat(),
            onValueChange = { onSeverityChanged(it.toInt()) },
            valueRange = 0f..10f,
            steps = 9,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectableChipGroup(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    HeadacheInsightSectionCard(title = title) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    label = { Text(localizedSymptomLabel(option)) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }
}

@Composable
fun EpisodeTimelineList(
    episodes: List<Episode>,
    modifier: Modifier = Modifier,
    onOpenEpisode: (String) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        episodes.forEach { episode ->
            HeadacheInsightSectionCard(
                title = episode.startedAt.toString(),
                supportingText = stringResource(
                    R.string.episode_timeline_supporting,
                    (episode.currentSeverity ?: "-").toString(),
                    localizedRedFlagStatus(episode.redFlagStatus),
                ),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(
                    text = episode.summaryText ?: stringResource(R.string.partial_entry),
                    style = MaterialTheme.typography.bodyMedium,
                )
                androidx.compose.material3.TextButton(onClick = { onOpenEpisode(episode.id) }) {
                    Text(stringResource(R.string.open_episode))
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
) {
    HeadacheInsightSectionCard(title = title, supportingText = subtitle) {}
}

@Composable
fun BottomMenuActions(
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.navigation_back))
        }
        Button(
            onClick = onHome,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.navigation_home))
        }
    }
}
