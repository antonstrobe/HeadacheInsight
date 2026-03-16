package com.neuron.headacheinsight.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.neuron.headacheinsight.core.model.SyncQueueItem
import com.neuron.headacheinsight.core.ui.BottomMenuActions
import com.neuron.headacheinsight.core.ui.EmptyState
import com.neuron.headacheinsight.core.ui.localizedSyncOperation
import com.neuron.headacheinsight.core.ui.localizedSyncStatus
import com.neuron.headacheinsight.domain.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    syncRepository: SyncRepository,
) : androidx.lifecycle.ViewModel() {
    val state: StateFlow<List<SyncQueueItem>> = syncRepository.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun SyncRoute(
    onBack: () -> Unit,
    onHome: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SyncScreen(
        items = state,
        onBack = onBack,
        onHome = onHome,
    )
}

@Composable
fun SyncScreen(
    items: List<SyncQueueItem>,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (items.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.sync_empty_title),
                    subtitle = stringResource(R.string.sync_empty_subtitle),
                )
            }
        }
        items(items, key = { it.id }) { item ->
            HeadacheInsightSectionCard(
                title = localizedSyncOperation(item.operationType),
                supportingText = stringResource(
                    R.string.sync_supporting,
                    localizedSyncStatus(item.status),
                    item.retryCount,
                ),
            ) {
                item.lastError?.let { Text(it) }
            }
        }
        item {
            BottomMenuActions(
                onBack = onBack,
                onHome = onHome,
            )
        }
    }
}
