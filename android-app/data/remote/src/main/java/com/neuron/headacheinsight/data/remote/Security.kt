package com.neuron.headacheinsight.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.neuron.headacheinsight.domain.ClientIdentityProvider
import com.neuron.headacheinsight.domain.CloudCredentialsRepository
import com.neuron.headacheinsight.domain.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl

@Singleton
class KeystoreClientIdentityProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : ClientIdentityProvider {
    private val alias = "headacheinsight_install_identity"

    override suspend fun installId(): String {
        val prefs = context.getSharedPreferences("install_identity", Context.MODE_PRIVATE)
        val existing = prefs.getString("install_id", null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("install_id", created).apply()
        ensureKeyPair()
        return created
    }

    override suspend fun publicKey(): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: ensureKeyPair()
        return Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
    }

    override suspend fun signature(payload: String): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: ensureKeyPair()
        val signature = Signature.getInstance("Ed25519")
        signature.initSign(entry.privateKey)
        signature.update(payload.encodeUtf8().toByteArray())
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    private fun ensureKeyPair(): KeyStore.PrivateKeyEntry {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) return existing

        val generator = KeyPairGenerator.getInstance("Ed25519", "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        ).setDigests(KeyProperties.DIGEST_NONE).build()
        generator.initialize(spec)
        generator.generateKeyPair()
        return keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }
}

@Singleton
class SignedHeadersInterceptor @Inject constructor(
    private val identityProvider: ClientIdentityProvider,
    private val cloudCredentialsRepository: CloudCredentialsRepository,
    private val settingsRepository: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val currentSettings = runBlocking { settingsRepository.observeSettings().first() }
        val cloudCredentials = runBlocking { cloudCredentialsRepository.observeCredentials().first() }
        val targetBaseUrl = runCatching {
            currentSettings.backendBaseUrl.toHttpUrl()
        }.getOrElse {
            DEFAULT_BACKEND_URL.toHttpUrl()
        }

        val rewritten = request.url.newBuilder()
            .scheme(targetBaseUrl.scheme)
            .host(targetBaseUrl.host)
            .port(targetBaseUrl.port)
            .build()

        val bodyBuffer = Buffer()
        request.body?.writeTo(bodyBuffer)
        val bodyBytes = bodyBuffer.readByteArray()
        val bodyHash = MessageDigest.getInstance("SHA-256").digest(bodyBytes)
        val bodyHashBase64 = Base64.encodeToString(bodyHash, Base64.NO_WRAP)
        val installId = runBlocking { identityProvider.installId() }
        val nonce = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis().toString()
        val payload = listOf(request.method, rewritten.encodedPath, timestamp, nonce, bodyHashBase64).joinToString(":")
        val signature = runBlocking { identityProvider.signature(payload) }

        val signedRequest = request.newBuilder()
            .url(rewritten)
            .header("X-Install-Id", installId)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Body-SHA256", bodyHashBase64)
            .header("X-Signature", signature)
            .apply {
                if (cloudCredentials.apiKey.isNotBlank()) {
                    header("X-OpenAI-Api-Key", cloudCredentials.apiKey)
                }
                header("X-OpenAI-Analysis-Model", cloudCredentials.analysisModel)
                header("X-OpenAI-Question-Model", cloudCredentials.questionModel)
                header("X-OpenAI-Transcribe-Model", cloudCredentials.transcribeModel)
            }
            .build()

        return chain.proceed(signedRequest)
    }
}

private const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8000/"

@Module
@InstallIn(SingletonComponent::class)
abstract class RemoteBindingsModule {
    @Binds
    abstract fun bindClientIdentityProvider(impl: KeystoreClientIdentityProvider): ClientIdentityProvider
}

@Module
@InstallIn(SingletonComponent::class)
object RemoteModule {
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        signedHeadersInterceptor: SignedHeadersInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(signedHeadersInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8000/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideBackendApi(retrofit: Retrofit): BackendApi = retrofit.create()
}
