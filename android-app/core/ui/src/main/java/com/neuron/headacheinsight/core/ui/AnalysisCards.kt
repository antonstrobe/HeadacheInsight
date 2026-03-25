package com.neuron.headacheinsight.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusBadge
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusColors
import com.neuron.headacheinsight.core.designsystem.preferredTextAlign
import com.neuron.headacheinsight.core.model.AllDataAnalysisOwnerId
import com.neuron.headacheinsight.core.model.AnalysisResponse
import com.neuron.headacheinsight.core.model.AnalysisSnapshot
import com.neuron.headacheinsight.core.model.AnalysisSource
import com.neuron.headacheinsight.core.model.AnalysisStatus
import com.neuron.headacheinsight.core.model.OwnerType
import com.neuron.headacheinsight.core.model.UrgentActionLevel
import com.neuron.headacheinsight.core.model.VoiceIntakeDraft
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val AnalysisCardJson = Json {
    ignoreUnknownKeys = true
}

private sealed interface AnalysisCardContent {
    data class Structured(val response: AnalysisResponse) : AnalysisCardContent
    data class VoiceDraft(val draft: VoiceIntakeDraft) : AnalysisCardContent
    data object Unknown : AnalysisCardContent
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisSnapshotCard(
    snapshot: AnalysisSnapshot,
    modifier: Modifier = Modifier,
    onOpenEpisode: (() -> Unit)? = null,
) {
    val content = remember(snapshot.responsePayloadJson) {
        snapshot.toAnalysisCardContent()
    }
    val isGlobal = snapshot.ownerType == OwnerType.PROFILE || snapshot.ownerId == AllDataAnalysisOwnerId
    var expanded by rememberSaveable(snapshot.id) { mutableStateOf(isGlobal) }

    val containerColor = when {
        isGlobal -> MaterialTheme.colorScheme.secondaryContainer
        content is AnalysisCardContent.VoiceDraft -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val accentColor = when {
        isGlobal -> MaterialTheme.colorScheme.secondary
        content is AnalysisCardContent.VoiceDraft -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    HeadacheInsightSectionCard(
        title = when {
            isGlobal -> stringResource(R.string.analysis_card_global_title)
            content is AnalysisCardContent.VoiceDraft -> stringResource(R.string.analysis_card_voice_title)
            content is AnalysisCardContent.Structured -> stringResource(R.string.analysis_card_episode_title)
            else -> stringResource(R.string.analysis_card_snapshot_title)
        },
        supportingText = stringResource(
            R.string.analysis_card_meta,
            snapshot.modelName,
            snapshot.createdAt.toString(),
        ),
        modifier = modifier,
        containerColor = containerColor,
        borderColor = accentColor.copy(alpha = 0.28f),
        accentColor = accentColor,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeadacheInsightStatusBadge(
                label = if (isGlobal) {
                    stringResource(R.string.analysis_scope_global)
                } else {
                    stringResource(R.string.analysis_scope_episode)
                },
                color = accentColor,
            )
            HeadacheInsightStatusBadge(
                label = when (snapshot.source) {
                    AnalysisSource.CLOUD -> stringResource(R.string.analysis_source_cloud)
                    AnalysisSource.LOCAL_RULE -> stringResource(R.string.analysis_source_local)
                },
                color = if (snapshot.source == AnalysisSource.CLOUD) {
                    HeadacheInsightStatusColors.CloudAnalyzed
                } else {
                    HeadacheInsightStatusColors.LocalComplete
                },
            )
            HeadacheInsightStatusBadge(
                label = when (snapshot.status) {
                    AnalysisStatus.COMPLETE -> stringResource(R.string.analysis_status_complete)
                    AnalysisStatus.FAILED -> stringResource(R.string.analysis_status_failed)
                    AnalysisStatus.QUEUED -> stringResource(R.string.analysis_status_queued)
                },
                color = if (snapshot.status == AnalysisStatus.COMPLETE) {
                    HeadacheInsightStatusColors.LocalComplete
                } else {
                    HeadacheInsightStatusColors.Warning
                },
            )
            val structured = (content as? AnalysisCardContent.Structured)?.response
            val urgentLevel = structured?.urgentAction?.level
            if (urgentLevel != null && urgentLevel != UrgentActionLevel.NONE) {
                HeadacheInsightStatusBadge(
                    label = localizedUrgentActionLabel(urgentLevel),
                    color = HeadacheInsightStatusColors.Warning,
                )
            }
        }

        when (content) {
            is AnalysisCardContent.Structured -> {
                content.response.urgentAction.userMessage
                    .takeIf { it.isNotBlank() && content.response.urgentAction.level != UrgentActionLevel.NONE }
                    ?.let { urgentMessage ->
                        Text(
                            text = urgentMessage,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = preferredTextAlign(),
                        )
                    }
                Text(
                    text = content.response.userSummary.plainLanguageSummary.ifBlank {
                        content.response.clinicianSummary.conciseMedicalContext
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = preferredTextAlign(),
                )
                if (expanded) {
                    content.response.clinicianSummary.conciseMedicalContext
                        .takeIf(String::isNotBlank)
                        ?.let { AnalysisTextSection(stringResource(R.string.analysis_clinician_summary), it) }
                    AnalysisChipSection(
                        title = stringResource(R.string.analysis_key_observations),
                        items = content.response.userSummary.keyObservations,
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_hypotheses),
                        items = content.response.hypotheses.map { "${it.label}: ${it.rationale}" },
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_patterns),
                        items = content.response.suspectedPatterns.map { "${it.type}: ${it.description}" },
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_doctor_points),
                        items = content.response.suggestedDoctorDiscussionPoints,
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_self_care),
                        items = content.response.selfCareGeneral,
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_tracking_fields),
                        items = content.response.suggestedTrackingFields,
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_suggested_attachments),
                        items = content.response.suggestedAttachments,
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_next_questions),
                        items = content.response.nextQuestions.map { it.prompt },
                    )
                    content.response.disclaimer.takeIf(String::isNotBlank)?.let { disclaimer ->
                        AnalysisTextSection(
                            title = stringResource(R.string.analysis_disclaimer),
                            text = disclaimer,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is AnalysisCardContent.VoiceDraft -> {
                Text(
                    text = content.draft.summaryText,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = preferredTextAlign(),
                )
                if (expanded) {
                    content.draft.severity?.let { severity ->
                        AnalysisTextSection(
                            title = stringResource(R.string.analysis_voice_severity_title),
                            text = stringResource(R.string.analysis_voice_severity, severity),
                        )
                    }
                    AnalysisChipSection(
                        title = stringResource(R.string.analysis_voice_symptoms),
                        items = content.draft.symptoms,
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_voice_notes),
                        items = content.draft.liveNotes,
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_voice_medications),
                        items = content.draft.medications,
                    )
                    AnalysisListSection(
                        title = stringResource(R.string.analysis_voice_fields),
                        items = content.draft.dynamicFields.map { "${it.section}: ${it.label} - ${it.value}" },
                    )
                }
            }

            AnalysisCardContent.Unknown -> {
                Text(
                    text = stringResource(R.string.analysis_unknown_body),
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = preferredTextAlign(),
                )
            }
        }

        SectionActionRow {
            if (!isGlobal && onOpenEpisode != null) {
                TextButton(onClick = onOpenEpisode) {
                    Text(stringResource(R.string.open_episode))
                }
            }
            if (content != AnalysisCardContent.Unknown) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) {
                            stringResource(R.string.analysis_hide_details)
                        } else {
                            stringResource(R.string.analysis_show_details)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisTextSection(
    title: String,
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    if (text.isBlank()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = preferredTextAlign(),
        )
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = color,
            textAlign = preferredTextAlign(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisChipSection(
    title: String,
    items: List<String>,
) {
    val normalized = items.map(String::trim).filter(String::isNotBlank).distinct()
    if (normalized.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = preferredTextAlign(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            normalized.forEach { item ->
                HeadacheInsightStatusBadge(
                    label = item,
                    color = HeadacheInsightStatusColors.CloudAnalyzed,
                )
            }
        }
    }
}

@Composable
private fun AnalysisListSection(
    title: String,
    items: List<String>,
) {
    val normalized = items.map(String::trim).filter(String::isNotBlank).distinct()
    if (normalized.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = preferredTextAlign(),
        )
        normalized.forEach { item ->
            Text(
                text = "• $item",
                modifier = Modifier.fillMaxWidth(),
                textAlign = preferredTextAlign(),
            )
        }
    }
}

private fun AnalysisSnapshot.toAnalysisCardContent(): AnalysisCardContent =
    runCatching {
        AnalysisCardContent.Structured(
            AnalysisCardJson.decodeFromString(AnalysisResponse.serializer(), responsePayloadJson),
        )
    }.getOrElse {
        runCatching {
            AnalysisCardContent.VoiceDraft(
                AnalysisCardJson.decodeFromString(VoiceIntakeDraft.serializer(), responsePayloadJson),
            )
        }.getOrElse {
            AnalysisCardContent.Unknown
        }
    }

@Composable
private fun localizedUrgentActionLabel(level: UrgentActionLevel): String = when (level) {
    UrgentActionLevel.NONE -> stringResource(R.string.red_flag_clear)
    UrgentActionLevel.MONITOR -> stringResource(R.string.red_flag_monitor)
    UrgentActionLevel.DISCUSS_SOON -> stringResource(R.string.red_flag_discuss_soon)
    UrgentActionLevel.URGENT -> stringResource(R.string.red_flag_urgent)
    UrgentActionLevel.EMERGENCY -> stringResource(R.string.red_flag_emergency)
}
