package com.neuron.headacheinsight.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neuron.headacheinsight.core.model.AttachmentType
import com.neuron.headacheinsight.core.model.ExtractionStatus
import com.neuron.headacheinsight.core.model.RedFlagStatus
import com.neuron.headacheinsight.core.model.SyncOperationType
import com.neuron.headacheinsight.core.model.SyncStatus
import com.neuron.headacheinsight.core.model.UploadStatus

@Composable
fun localizedRedFlagStatus(status: RedFlagStatus): String = stringResource(
    when (status) {
        RedFlagStatus.CLEAR -> R.string.red_flag_clear
        RedFlagStatus.MONITOR -> R.string.red_flag_monitor
        RedFlagStatus.DISCUSS_SOON -> R.string.red_flag_discuss_soon
        RedFlagStatus.URGENT -> R.string.red_flag_urgent
        RedFlagStatus.EMERGENCY -> R.string.red_flag_emergency
    },
)

@Composable
fun localizedAttachmentType(type: AttachmentType): String = stringResource(
    when (type) {
        AttachmentType.IMAGE -> R.string.attachment_type_image
        AttachmentType.PDF -> R.string.attachment_type_pdf
        AttachmentType.AUDIO -> R.string.attachment_type_audio
        AttachmentType.DOCUMENT -> R.string.attachment_type_document
        AttachmentType.SCREENSHOT -> R.string.attachment_type_screenshot
    },
)

@Composable
fun localizedExtractionStatus(status: ExtractionStatus): String = stringResource(
    when (status) {
        ExtractionStatus.NOT_STARTED -> R.string.extraction_not_started
        ExtractionStatus.LOCAL_EXTRACTED -> R.string.extraction_local_extracted
        ExtractionStatus.CLOUD_QUEUED -> R.string.extraction_cloud_queued
        ExtractionStatus.CLOUD_COMPLETE -> R.string.extraction_cloud_complete
        ExtractionStatus.FAILED -> R.string.extraction_failed
    },
)

@Composable
fun localizedUploadStatus(status: UploadStatus): String = stringResource(
    when (status) {
        UploadStatus.LOCAL_ONLY -> R.string.upload_local_only
        UploadStatus.QUEUED -> R.string.upload_queued
        UploadStatus.UPLOADING -> R.string.upload_uploading
        UploadStatus.UPLOADED -> R.string.upload_uploaded
        UploadStatus.FAILED -> R.string.upload_failed
    },
)

@Composable
fun localizedSyncStatus(status: SyncStatus): String = stringResource(
    when (status) {
        SyncStatus.PENDING -> R.string.sync_status_pending
        SyncStatus.RUNNING -> R.string.sync_status_running
        SyncStatus.FAILED -> R.string.sync_status_failed
        SyncStatus.COMPLETE -> R.string.sync_status_complete
    },
)

@Composable
fun localizedSyncOperation(type: SyncOperationType): String = stringResource(
    when (type) {
        SyncOperationType.REGISTER_CLIENT -> R.string.sync_op_register_client
        SyncOperationType.TRANSCRIBE_AUDIO -> R.string.sync_op_transcribe_audio
        SyncOperationType.ANALYZE_EPISODE -> R.string.sync_op_analyze_episode
        SyncOperationType.GENERATE_QUESTIONS -> R.string.sync_op_generate_questions
        SyncOperationType.ANALYZE_ATTACHMENT -> R.string.sync_op_analyze_attachment
        SyncOperationType.UPLOAD_ATTACHMENT -> R.string.sync_op_upload_attachment
    },
)

@Composable
fun localizedSymptomLabel(code: String): String {
    val resId = when (code) {
        "nausea" -> R.string.symptom_nausea
        "photophobia" -> R.string.symptom_photophobia
        "phonophobia" -> R.string.symptom_phonophobia
        "dizziness" -> R.string.symptom_dizziness
        "aura" -> R.string.symptom_aura
        "neck pain" -> R.string.symptom_neck_pain
        else -> return code
    }
    return stringResource(resId)
}

@Composable
fun localizedTriggerLabel(code: String): String {
    val resId = when (code) {
        "sleep_disruption" -> R.string.trigger_sleep_disruption
        "stress" -> R.string.trigger_stress
        "hydration" -> R.string.trigger_hydration
        else -> return code
    }
    return stringResource(resId)
}
