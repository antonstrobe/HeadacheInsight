package com.neuron.headacheinsight.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HeadacheInsightSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    containerColor: Color? = null,
    borderColor: Color? = null,
    accentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val resolvedContainerColor = containerColor ?: MaterialTheme.colorScheme.surfaceVariant
    val resolvedBorderColor = borderColor ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    val resolvedAccentColor = accentColor ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = HeadacheInsightCardDefaults.elevated(containerColor = resolvedContainerColor),
        border = BorderStroke(1.dp, resolvedBorderColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = preferredHorizontalAlignment(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .background(resolvedAccentColor, MaterialTheme.shapes.small)
                    .align(preferredHorizontalAlignment()),
            )
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = preferredTextAlign(),
            )
            supportingText?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = preferredTextAlign(),
                )
            }
            content()
        }
    }
}

@Composable
fun HeadacheInsightStatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    AssistChip(
        onClick = { onClick?.invoke() },
        modifier = modifier,
        label = { Text(label) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.16f),
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun HeadacheInsightPrimaryPainButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = preferredSpacedArrangement(14.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun KeyValueLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = preferredSpacedArrangement(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun headacheInsightActionButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
