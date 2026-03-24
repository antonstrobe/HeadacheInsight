package com.neuron.headacheinsight.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightCardDefaults
import com.neuron.headacheinsight.core.designsystem.LocalHandPreference
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.preferredHorizontalAlignment
import com.neuron.headacheinsight.core.designsystem.preferredHorizontalArrangement
import com.neuron.headacheinsight.core.designsystem.preferredSpacedArrangement
import com.neuron.headacheinsight.core.designsystem.preferredTextAlign
import com.neuron.headacheinsight.core.model.Episode
import com.neuron.headacheinsight.core.model.HandPreference

@Composable
fun SeveritySlider(
    severity: Int,
    onSeverityChanged: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = preferredHorizontalAlignment(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.severity_now, severity),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = preferredTextAlign(),
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
            horizontalArrangement = preferredSpacedArrangement(8.dp),
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
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = preferredTextAlign(),
                )
                SectionActionRow {
                    androidx.compose.material3.TextButton(onClick = { onOpenEpisode(episode.id) }) {
                        Text(stringResource(R.string.open_episode))
                    }
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
fun SectionActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = preferredHorizontalArrangement(),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ToggleSectionCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = HeadacheInsightCardDefaults.elevated(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = preferredHorizontalAlignment(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (LocalHandPreference.current == HandPreference.LEFT) {
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                    )
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = preferredTextAlign(),
                )
                if (LocalHandPreference.current == HandPreference.RIGHT) {
                    Switch(
                        checked = checked,
                        onCheckedChange = onCheckedChange,
                    )
                }
            }
            supportingText?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = preferredTextAlign(),
                )
            }
        }
    }
}

@Composable
fun BottomMenuActions(
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handPreference = LocalHandPreference.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (handPreference == HandPreference.LEFT) {
            Button(
                onClick = onHome,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.navigation_home))
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.navigation_back))
            }
        } else {
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
}
