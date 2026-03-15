package com.neuron.headacheinsight.feature.attachments

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
import com.neuron.headacheinsight.core.model.Attachment
import com.neuron.headacheinsight.core.ui.localizedAttachmentType
import com.neuron.headacheinsight.core.ui.localizedExtractionStatus
import com.neuron.headacheinsight.core.ui.localizedUploadStatus
import com.neuron.headacheinsight.domain.AttachmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AttachmentsViewModel @Inject constructor(
    attachmentRepository: AttachmentRepository,
) : androidx.lifecycle.ViewModel() {
    val state: StateFlow<List<Attachment>> = attachmentRepository.observeAttachments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun AttachmentsRoute(
    viewModel: AttachmentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AttachmentsScreen(state)
}

@Composable
fun AttachmentsScreen(
    attachments: List<Attachment>,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            HeadacheInsightSectionCard(
                title = attachment.displayName,
                supportingText = stringResource(
                    R.string.attachments_meta,
                    localizedAttachmentType(attachment.type),
                    localizedExtractionStatus(attachment.extractionStatus),
                    localizedUploadStatus(attachment.uploadStatus),
                ),
            ) {
                Text(attachment.localUriOrPath)
                attachment.extractedText?.let { Text(it.take(240)) }
            }
        }
    }
}
