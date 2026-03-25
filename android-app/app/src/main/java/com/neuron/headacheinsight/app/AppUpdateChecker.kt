package com.neuron.headacheinsight.app

import com.neuron.headacheinsight.BuildConfig
import com.neuron.headacheinsight.core.common.DispatchersProvider
import com.neuron.headacheinsight.core.common.NetworkSnapshotProvider
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class AppUpdateInfo(
    val currentSha: String,
    val latestSha: String,
    val updateUrl: String,
)

@Serializable
private data class GitHubCommitResponse(
    val sha: String,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Singleton
class AppUpdateChecker @Inject constructor(
    private val json: Json,
    private val networkSnapshotProvider: NetworkSnapshotProvider,
    private val dispatchersProvider: DispatchersProvider,
) {
    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(dispatchersProvider.io) {
        val currentSha = BuildConfig.APP_GIT_SHA.trim()
        if (currentSha.isBlank() || currentSha.equals("unknown", ignoreCase = true)) {
            return@withContext null
        }

        val network = networkSnapshotProvider.currentSnapshot()
        if (!network.connected || !network.validated) {
            return@withContext null
        }

        val repoUrl = BuildConfig.APP_REPO_URL.removeSuffix("/")
        if (!repoUrl.startsWith("https://github.com/", ignoreCase = true)) {
            return@withContext null
        }
        val repoPath = repoUrl.removePrefix("https://github.com/").trim('/')
        if (repoPath.isBlank()) {
            return@withContext null
        }

        val connection = (URL("https://api.github.com/repos/$repoPath/commits/main").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

        try {
            if (connection.responseCode !in 200..299) {
                return@withContext null
            }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val remote = json.decodeFromString(GitHubCommitResponse.serializer(), payload)
            val latestSha = remote.sha.trim()
            if (latestSha.isBlank() || latestSha.startsWith(currentSha, ignoreCase = true)) {
                return@withContext null
            }
            AppUpdateInfo(
                currentSha = currentSha,
                latestSha = latestSha.take(12),
                updateUrl = BuildConfig.APP_RELEASES_URL.takeIf { it.isNotBlank() } ?: repoUrl,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
