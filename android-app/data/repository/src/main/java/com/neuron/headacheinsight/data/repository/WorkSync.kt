package com.neuron.headacheinsight.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.neuron.headacheinsight.core.common.DispatchersProvider
import com.neuron.headacheinsight.core.common.TimeProvider
import com.neuron.headacheinsight.core.model.EpisodeTranscript
import com.neuron.headacheinsight.core.model.SyncOperationType
import com.neuron.headacheinsight.core.model.SyncQueueItem
import com.neuron.headacheinsight.core.model.SyncStatus
import com.neuron.headacheinsight.core.model.TranscriptVariant
import com.neuron.headacheinsight.domain.AudioRecorder
import com.neuron.headacheinsight.domain.CloudSpeechRecognizerEngine
import com.neuron.headacheinsight.domain.EpisodeRepository
import com.neuron.headacheinsight.domain.LocalSpeechRecognizerEngine
import com.neuron.headacheinsight.domain.SyncRepository
import com.neuron.headacheinsight.domain.SyncScheduler
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioCaptureManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchersProvider: DispatchersProvider,
) : AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var currentFile: File? = null

    override suspend fun start(episodeId: String): Result<String> = runCatching {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            error("RECORD_AUDIO permission is required")
        }
        val sampleRate = 16_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize,
        )
        val outputDir = File(context.filesDir, "audio").apply { mkdirs() }
        val outputFile = File(outputDir, "${episodeId}_${System.currentTimeMillis()}.wav")
        writeWavHeader(outputFile, sampleRate, 1, 16)
        val buffer = ByteArray(minBufferSize)
        audioRecord = recorder
        currentFile = outputFile
        recorder.startRecording()
        recordingJob = CoroutineScope(dispatchersProvider.io).launch {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(44L)
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        raf.write(buffer, 0, read)
                    }
                }
            }
            updateWavHeader(outputFile)
        }
        outputFile.absolutePath
    }

    override suspend fun stop(): Result<String?> = runCatching {
        val recorder = audioRecord ?: return@runCatching currentFile?.absolutePath
        recordingJob?.cancelAndJoin()
        recorder.stop()
        recorder.release()
        audioRecord = null
        currentFile?.absolutePath
    }

    private fun writeWavHeader(file: File, sampleRate: Int, channels: Int, bitDepth: Int) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.writeBytes("RIFF")
            raf.writeInt(Integer.reverseBytes(36))
            raf.writeBytes("WAVE")
            raf.writeBytes("fmt ")
            raf.writeInt(Integer.reverseBytes(16))
            raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
            raf.writeInt(Integer.reverseBytes(sampleRate))
            raf.writeInt(Integer.reverseBytes(sampleRate * channels * bitDepth / 8))
            raf.writeShort(java.lang.Short.reverseBytes((channels * bitDepth / 8).toShort()).toInt())
            raf.writeShort(java.lang.Short.reverseBytes(bitDepth.toShort()).toInt())
            raf.writeBytes("data")
            raf.writeInt(0)
        }
    }

    private fun updateWavHeader(file: File) {
        RandomAccessFile(file, "rw").use { raf ->
            val fileLength = raf.length().toInt()
            raf.seek(4)
            raf.writeInt(Integer.reverseBytes(fileLength - 8))
            raf.seek(40)
            raf.writeInt(Integer.reverseBytes(fileLength - 44))
        }
    }
}

@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository,
    private val timeProvider: TimeProvider,
) : SyncScheduler {
    override suspend fun enqueueEpisodeSync(episodeId: String) {
        val item = SyncQueueItem(
            id = UUID.randomUUID().toString(),
            operationType = SyncOperationType.ANALYZE_EPISODE,
            payload = episodeId,
            retryCount = 0,
            status = SyncStatus.PENDING,
            createdAt = timeProvider.now(),
        )
        syncRepository.enqueue(item)
        val request = OneTimeWorkRequestBuilder<EpisodeAnalysisWorker>()
            .setInputData(Data.Builder().putString("queue_id", item.id).putString("episode_id", episodeId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    override suspend fun enqueueTranscription(audioPath: String, episodeId: String) {
        val item = SyncQueueItem(
            id = UUID.randomUUID().toString(),
            operationType = SyncOperationType.TRANSCRIBE_AUDIO,
            payload = audioPath,
            retryCount = 0,
            status = SyncStatus.PENDING,
            createdAt = timeProvider.now(),
        )
        syncRepository.enqueue(item)
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(Data.Builder().putString("queue_id", item.id).putString("audio_path", audioPath).putString("episode_id", episodeId).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val localSpeechRecognizerEngine: LocalSpeechRecognizerEngine,
    private val cloudSpeechRecognizerEngine: CloudSpeechRecognizerEngine,
    private val episodeRepository: EpisodeRepository,
    private val syncRepository: SyncRepository,
    private val timeProvider: TimeProvider,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val queueId = inputData.getString("queue_id") ?: return Result.failure()
        val audioPath = inputData.getString("audio_path") ?: return Result.failure()
        val episodeId = inputData.getString("episode_id") ?: return Result.failure()
        syncRepository.markRunning(queueId)

        val localResult: kotlin.Result<EpisodeTranscript> = if (localSpeechRecognizerEngine.isAvailable()) {
            localSpeechRecognizerEngine.transcribeAudio(audioPath, null)
        } else {
            kotlin.Result.failure(IllegalStateException("Local ASR unavailable"))
        }

        if (localResult.isSuccess) {
            episodeRepository.upsertTranscript(localResult.getOrThrow())
            syncRepository.markComplete(queueId)
            return Result.success()
        }

        val cloudResult: kotlin.Result<EpisodeTranscript> = cloudSpeechRecognizerEngine.transcribeAudio(audioPath, null)
        return if (cloudResult.isSuccess) {
            episodeRepository.upsertTranscript(cloudResult.getOrThrow())
            syncRepository.markComplete(queueId)
            Result.success()
        } else {
            episodeRepository.upsertTranscript(
                EpisodeTranscript(
                    id = UUID.randomUUID().toString(),
                    episodeId = episodeId,
                    rawAudioPath = audioPath,
                    transcriptText = null,
                    language = null,
                    engineType = "local",
                    variant = TranscriptVariant.LOCAL,
                    confidence = null,
                    createdAt = timeProvider.now(),
                ),
            )
            syncRepository.markFailed(queueId, cloudResult.exceptionOrNull()?.message ?: "Transcription failed")
            Result.retry()
        }
    }
}

@HiltWorker
class EpisodeAnalysisWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val analysisRepository: com.neuron.headacheinsight.domain.AnalysisRepository,
    private val syncRepository: SyncRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val queueId = inputData.getString("queue_id") ?: return Result.failure()
        val episodeId = inputData.getString("episode_id") ?: return Result.failure()
        syncRepository.markRunning(queueId)
        return analysisRepository.analyzeEpisode(episodeId).fold(
            onSuccess = {
                syncRepository.markComplete(queueId)
                Result.success()
            },
            onFailure = {
                syncRepository.markFailed(queueId, it.message ?: "Analysis failed")
                Result.retry()
            },
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingsModule {
    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindAudioRecorder(impl: AudioCaptureManager): AudioRecorder
}
