package com.neuron.headacheinsight.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.neuron.headacheinsight.core.model.LocalSpeechPackState
import com.neuron.headacheinsight.core.model.LocalSpeechPackStatus
import com.neuron.headacheinsight.domain.LocalSpeechPackManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Singleton
class AndroidOnDeviceSpeechPackManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalSpeechPackManager {
    private val state = MutableStateFlow(LocalSpeechPackState())
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun observeState(): Flow<LocalSpeechPackState> = state.asStateFlow()

    override suspend fun refresh(languageTag: String) {
        if (!isApiSupported()) {
            state.value = LocalSpeechPackState(
                status = LocalSpeechPackStatus.UNSUPPORTED,
                message = "On-device speech APIs are unavailable on this Android version.",
            )
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            state.value = LocalSpeechPackState(
                status = LocalSpeechPackStatus.NOT_INSTALLED,
                message = "On-device recognition service is not available yet.",
            )
            return
        }

        state.value = state.value.copy(status = LocalSpeechPackStatus.CHECKING, progressPercent = null, message = null)
        withRecognizer { recognizer, intent, finish ->
            recognizer.checkRecognitionSupport(
                intent,
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        val installed = recognitionSupport.getInstalledOnDeviceLanguages().containsLanguage(languageTag)
                        val pending = recognitionSupport.getPendingOnDeviceLanguages().containsLanguage(languageTag)
                        val supported = recognitionSupport.getSupportedOnDeviceLanguages().containsLanguage(languageTag) || installed || pending
                        state.value = when {
                            installed -> LocalSpeechPackState(
                                status = LocalSpeechPackStatus.READY,
                                message = "Local speech pack is installed.",
                            )
                            pending -> LocalSpeechPackState(
                                status = LocalSpeechPackStatus.SCHEDULED,
                                message = "Model download is scheduled by the system.",
                            )
                            supported -> LocalSpeechPackState(
                                status = LocalSpeechPackStatus.NOT_INSTALLED,
                                message = "Local speech pack is available for download.",
                            )
                            else -> LocalSpeechPackState(
                                status = LocalSpeechPackStatus.UNSUPPORTED,
                                message = "Selected language is not supported by the on-device speech service.",
                            )
                        }
                        finish()
                    }

                    override fun onError(error: Int) {
                        state.value = LocalSpeechPackState(
                            status = LocalSpeechPackStatus.ERROR,
                            message = "Failed to check local speech support (code $error).",
                        )
                        finish()
                    }
                },
            )
        }
    }

    override suspend fun install(languageTag: String) {
        if (!isApiSupported()) {
            state.value = LocalSpeechPackState(
                status = LocalSpeechPackStatus.UNSUPPORTED,
                message = "On-device speech APIs are unavailable on this Android version.",
            )
            return
        }
        state.value = LocalSpeechPackState(
            status = LocalSpeechPackStatus.INSTALLING,
            progressPercent = 0,
            message = "Preparing local speech pack download...",
        )
        withRecognizer(languageTag) { recognizer, intent, finish ->
            recognizer.triggerModelDownload(
                intent,
                context.mainExecutor,
                object : ModelDownloadListener {
                    override fun onProgress(completedPercent: Int) {
                        state.value = LocalSpeechPackState(
                            status = LocalSpeechPackStatus.INSTALLING,
                            progressPercent = completedPercent,
                            message = "Downloading local speech pack...",
                        )
                    }

                    override fun onSuccess() {
                        state.value = LocalSpeechPackState(
                            status = LocalSpeechPackStatus.READY,
                            progressPercent = 100,
                            message = "Local speech pack installed.",
                        )
                        finish()
                    }

                    override fun onScheduled() {
                        state.value = LocalSpeechPackState(
                            status = LocalSpeechPackStatus.SCHEDULED,
                            message = "Download scheduled by Android. Keep the device online and check status again.",
                        )
                        finish()
                    }

                    override fun onError(error: Int) {
                        state.value = LocalSpeechPackState(
                            status = LocalSpeechPackStatus.ERROR,
                            message = "Failed to install local speech pack (code $error).",
                        )
                        finish()
                    }
                },
            )
        }
        refresh(languageTag)
    }

    private suspend fun withRecognizer(
        languageTag: String = "ru-RU",
        action: (SpeechRecognizer, Intent, finish: () -> Unit) -> Unit,
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
            mainHandler.post {
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                var finished = false
                val finish = {
                    if (!finished) {
                        finished = true
                        recognizer.destroy()
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
                continuation.invokeOnCancellation {
                    recognizer.destroy()
                }
                action(recognizer, intent, finish)
            }
        }
    }

    private fun isApiSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun List<String>.containsLanguage(languageTag: String): Boolean {
        val requested = languageTag.substringBefore('-').lowercase()
        return any { candidate ->
            candidate.equals(languageTag, ignoreCase = true) ||
                candidate.substringBefore('-').lowercase() == requested
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalSpeechPackBindingsModule {
    @Binds
    abstract fun bindLocalSpeechPackManager(
        impl: AndroidOnDeviceSpeechPackManager,
    ): LocalSpeechPackManager
}
