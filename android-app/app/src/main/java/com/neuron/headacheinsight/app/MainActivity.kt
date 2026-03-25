package com.neuron.headacheinsight.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neuron.headacheinsight.R
import com.neuron.headacheinsight.core.designsystem.HeadacheInsightTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeadacheInsightApp()
        }
    }
}

@Composable
private fun HeadacheInsightApp(
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val state = appViewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current

    HeadacheInsightTheme(
        comfortMode = state.settings.comfortModeEnabled,
        textScale = state.settings.textScale,
    ) {
        HeadacheInsightNavHost(appViewModel = appViewModel)
        state.updateInfo?.let { update ->
            AlertDialog(
                onDismissRequest = appViewModel::dismissAppUpdatePrompt,
                title = { Text(stringResource(R.string.update_dialog_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.update_dialog_message,
                            update.currentSha,
                            update.latestSha,
                        ),
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            appViewModel.dismissAppUpdatePrompt()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.updateUrl)))
                            }
                        },
                    ) {
                        Text(stringResource(R.string.update_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = appViewModel::dismissAppUpdatePrompt) {
                        Text(stringResource(R.string.update_dialog_dismiss))
                    }
                },
            )
        }
    }
}
