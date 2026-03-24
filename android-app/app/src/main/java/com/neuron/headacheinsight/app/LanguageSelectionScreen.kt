package com.neuron.headacheinsight.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neuron.headacheinsight.R
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightSectionCard
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightStatusColors
import com.neuron.headacheinsight.core.designsystem.preferredHorizontalAlignment
import com.neuron.headacheinsight.core.designsystem.preferredSpacedArrangement
import com.neuron.headacheinsight.core.designsystem.preferredTextAlign

@Composable
fun LanguageSelectionScreen(
    onSelectRussian: () -> Unit,
    onSelectEnglish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = preferredHorizontalAlignment(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeadacheInsightSectionCard(
            title = stringResource(R.string.language_screen_title),
            supportingText = stringResource(R.string.language_screen_subtitle),
        ) {
            Text(
                text = stringResource(R.string.language_screen_description),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = preferredTextAlign(),
            )
        }

        LanguageOptionButton(
            badge = "RU",
            title = stringResource(R.string.language_option_russian),
            subtitle = stringResource(R.string.language_option_russian_subtitle),
            onClick = onSelectRussian,
        )

        LanguageOptionButton(
            badge = "EN",
            title = stringResource(R.string.language_option_english),
            subtitle = stringResource(R.string.language_option_english_subtitle),
            onClick = onSelectEnglish,
        )

        HeadacheInsightSectionCard(
            title = stringResource(R.string.language_next_title),
            supportingText = stringResource(R.string.language_next_subtitle),
        ) {
            Text(
                text = stringResource(R.string.language_next_description),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = preferredTextAlign(),
            )
        }
    }
}

@Composable
private fun LanguageOptionButton(
    badge: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = preferredSpacedArrangement(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(HeadacheInsightStatusColors.CloudAnalyzed, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(text = badge, color = MaterialTheme.colorScheme.onPrimary)
            }
            Column(
                horizontalAlignment = preferredHorizontalAlignment(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = subtitle,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}
