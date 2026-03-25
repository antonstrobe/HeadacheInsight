package com.neuron.headacheinsight.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.neuron.headacheinsight.core.model.CloudCredentials
import com.neuron.headacheinsight.core.model.OpenAiAutoModelId
import com.neuron.headacheinsight.domain.CloudCredentialsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreBackedCloudCredentialsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : CloudCredentialsRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readCredentials())

    override fun observeCredentials(): Flow<CloudCredentials> = state.asStateFlow()

    override suspend fun saveCredentials(credentials: CloudCredentials) {
        prefs.edit()
            .putString(KEY_API_KEY, credentials.apiKey.takeIf { it.isNotBlank() }?.let(::encrypt))
            .putString(KEY_ANALYSIS_MODEL, credentials.analysisModel.takeIf { it.isNotBlank() }?.let(::encrypt))
            .putString(KEY_ALL_DATA_ANALYSIS_MODEL, credentials.allDataAnalysisModel.takeIf { it.isNotBlank() }?.let(::encrypt))
            .putString(KEY_QUESTION_MODEL, credentials.questionModel.takeIf { it.isNotBlank() }?.let(::encrypt))
            .putString(KEY_TRANSCRIBE_MODEL, credentials.transcribeModel.takeIf { it.isNotBlank() }?.let(::encrypt))
            .apply()
        state.value = credentials.normalized()
    }

    private fun readCredentials(): CloudCredentials = CloudCredentials(
        apiKey = prefs.getString(KEY_API_KEY, null)?.let(::decryptOrNull).orEmpty(),
        analysisModel = prefs.getString(KEY_ANALYSIS_MODEL, null)?.let(::decryptOrNull).orEmpty().ifBlank { DEFAULT_ANALYSIS_MODEL },
        allDataAnalysisModel = prefs.getString(KEY_ALL_DATA_ANALYSIS_MODEL, null)?.let(::decryptOrNull).orEmpty().ifBlank { DEFAULT_ALL_DATA_ANALYSIS_MODEL },
        questionModel = prefs.getString(KEY_QUESTION_MODEL, null)?.let(::decryptOrNull).orEmpty().ifBlank { DEFAULT_QUESTION_MODEL },
        transcribeModel = prefs.getString(KEY_TRANSCRIBE_MODEL, null)?.let(::decryptOrNull).orEmpty().ifBlank { DEFAULT_TRANSCRIBE_MODEL },
    ).normalized()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.encodeToByteArray())
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        require(payload.size > IV_SIZE) { "Encrypted payload is invalid." }
        val iv = payload.copyOfRange(0, IV_SIZE)
        val encrypted = payload.copyOfRange(IV_SIZE, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_SIZE, iv))
        return cipher.doFinal(encrypted).decodeToString()
    }

    private fun decryptOrNull(value: String): String? = runCatching { decrypt(value) }.getOrNull()

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun CloudCredentials.normalized(): CloudCredentials = copy(
        analysisModel = analysisModel.ifBlank { DEFAULT_ANALYSIS_MODEL },
        allDataAnalysisModel = allDataAnalysisModel.ifBlank { DEFAULT_ALL_DATA_ANALYSIS_MODEL },
        questionModel = when (questionModel.ifBlank { DEFAULT_QUESTION_MODEL }) {
            LEGACY_QUESTION_MODEL -> DEFAULT_QUESTION_MODEL
            else -> questionModel.ifBlank { DEFAULT_QUESTION_MODEL }
        },
        transcribeModel = transcribeModel.ifBlank { DEFAULT_TRANSCRIBE_MODEL },
    )

    private companion object {
        const val PREFS_NAME = "secure_cloud_credentials"
        const val KEY_ALIAS = "headacheinsight_cloud_secret"
        const val KEY_API_KEY = "openai_api_key"
        const val KEY_ANALYSIS_MODEL = "openai_analysis_model"
        const val KEY_ALL_DATA_ANALYSIS_MODEL = "openai_all_data_analysis_model"
        const val KEY_QUESTION_MODEL = "openai_question_model"
        const val KEY_TRANSCRIBE_MODEL = "openai_transcribe_model"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val GCM_TAG_SIZE = 128
        const val DEFAULT_ANALYSIS_MODEL = OpenAiAutoModelId
        const val DEFAULT_ALL_DATA_ANALYSIS_MODEL = OpenAiAutoModelId
        const val DEFAULT_QUESTION_MODEL = OpenAiAutoModelId
        const val DEFAULT_TRANSCRIBE_MODEL = OpenAiAutoModelId
        const val LEGACY_QUESTION_MODEL = "gpt-4.1-mini"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SecureCloudCredentialsBindingsModule {
    @Binds
    abstract fun bindCloudCredentialsRepository(
        impl: KeystoreBackedCloudCredentialsRepository,
    ): CloudCredentialsRepository
}
