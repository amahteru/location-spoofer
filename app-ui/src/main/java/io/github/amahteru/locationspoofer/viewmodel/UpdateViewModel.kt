package io.github.amahteru.locationspoofer.viewmodel

import android.app.DownloadManager
import android.content.Context
import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.amahteru.locationspoofer.data.repository.SettingsRepository
import io.github.amahteru.locationspoofer.ui.R
import io.github.amahteru.locationspoofer.ui.components.AppSnackbar
import io.github.amahteru.locationspoofer.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import io.github.amahteru.locationspoofer.ui.BuildConfig

data class LatestBuild(
    val sha: String,
    val runId: Long,
    val runUrl: String,
    val createdAt: String,
    val compareStatus: String
) {
    val shortSha: String get() = sha.take(7)
    val hasUpdate: Boolean get() = compareStatus == "ahead" || compareStatus == "diverged"
    val isUncomparable: Boolean get() = compareStatus == "unknown"
}

data class UpdateUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val latestBuild: LatestBuild? = null,
    val hasGithubToken: Boolean = false,
    val isResolvingDownload: Boolean = false,
    val activeDownloadId: Long? = null,
    val downloadProgress: Int = 0,
    val downloadStatus: Int = DownloadManager.STATUS_PENDING
)

class UpdateViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val _uiState = MutableStateFlow(
        UpdateUiState(hasGithubToken = settingsRepository.githubToken.isNotBlank())
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val updateManager = UpdateManager(context)
    private val okHttpClient = OkHttpClient()
    private val noRedirectClient = okHttpClient.newBuilder().followRedirects(false).build()

    val githubToken: String get() = settingsRepository.githubToken

    fun fetchLatestBuild() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val build = loadLatestBuild()
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, latestBuild = build) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            }
        }
    }

    private fun loadLatestBuild(): LatestBuild {
        val runs = getJson("$API/actions/workflows/$WORKFLOW/runs?branch=main&event=push&status=success&per_page=1")
            ?: throw Exception("Failed to fetch builds")
        val run = runs.optJSONArray("workflow_runs")?.optJSONObject(0)
            ?: throw Exception("No successful build")
        val headSha = run.getString("head_sha")

        val compareStatus = if (headSha == BuildConfig.COMMIT_SHA) {
            "identical"
        } else {
            getJson("$API/compare/${BuildConfig.COMMIT_SHA}...$headSha?per_page=1")
                ?.optString("status", "unknown") ?: "unknown"
        }

        return LatestBuild(
            sha = headSha,
            runId = run.getLong("id"),
            runUrl = run.optString("html_url"),
            createdAt = run.optString("created_at"),
            compareStatus = compareStatus
        )
    }

    private fun findArtifact(runId: Long): JSONObject? {
        val artifacts = getJson("$API/actions/runs/$runId/artifacts")?.optJSONArray("artifacts") ?: return null
        for (i in 0 until artifacts.length()) {
            val artifact = artifacts.getJSONObject(i)
            val name = artifact.optString("name")
            if (!artifact.optBoolean("expired") && name.endsWith(".apk") &&
                !name.contains("unsigned") && isArtifactForCurrentScheme(name)
            ) {
                return artifact
            }
        }
        return null
    }

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw Exception("GitHub API ${response.code}")
            return JSONObject(response.body.string())
        }
    }

    fun saveGithubToken(token: String) {
        val trimmed = token.trim()
        settingsRepository.githubToken = trimmed
        _uiState.update { it.copy(hasGithubToken = trimmed.isNotEmpty()) }
    }

    fun getIgnoredSha(): String = settingsRepository.getIgnoredVersion()

    fun ignoreBuild(sha: String) {
        settingsRepository.setIgnoredVersion(sha)
    }

    fun startUpdate() {
        val build = _uiState.value.latestBuild ?: return
        val token = settingsRepository.githubToken
        if (token.isBlank()) {
            openInBrowser(build.runUrl)
            return
        }

        _uiState.update { it.copy(isResolvingDownload = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val artifact = runCatching { findArtifact(build.runId) }.getOrNull()
            val downloadUrl = artifact?.let {
                runCatching { resolveArtifactUrl(it.optString("archive_download_url"), token) }.getOrNull()
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isResolvingDownload = false) }
                if (artifact == null || downloadUrl == null) {
                    AppSnackbar.show(context.getString(R.string.github_token_download_failed), long = true)
                    openInBrowser(build.runUrl)
                } else {
                    startDownload(downloadUrl, artifact.optString("name"))
                }
            }
        }
    }

    private fun resolveArtifactUrl(artifactUrl: String, token: String): String? {
        val request = Request.Builder()
            .url(artifactUrl)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .build()
        noRedirectClient.newCall(request).execute().use { response ->
            return if (response.isRedirect) response.header("Location") else null
        }
    }

    private fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun startDownload(url: String, fileName: String) {
        val downloadId = updateManager.downloadApk(url, fileName)
        _uiState.update {
            it.copy(
                activeDownloadId = downloadId,
                downloadProgress = 0,
                downloadStatus = DownloadManager.STATUS_PENDING
            )
        }
        monitorDownload(downloadId)
    }

    private fun monitorDownload(downloadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val status = updateManager.getDownloadStatus(downloadId)
                val progress = updateManager.getDownloadProgress(downloadId)

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            downloadStatus = status,
                            downloadProgress = progress
                        )
                    }
                }

                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    break
                }
                delay(500)
            }
        }
    }

    fun installApk() {
        _uiState.value.activeDownloadId?.let { downloadId ->
            updateManager.installApk(downloadId)
        }
    }

    fun cancelDownload() {
        _uiState.value.activeDownloadId?.let { downloadId ->
            updateManager.cancelDownload(downloadId)
            _uiState.update {
                it.copy(
                    activeDownloadId = null,
                    downloadProgress = 0,
                    downloadStatus = DownloadManager.STATUS_PENDING
                )
            }
        }
    }

    private companion object {
        const val API = "https://api.github.com/repos/amahteru/location-spoofer"
        const val WORKFLOW = "build.yml"
    }
}

private fun isArtifactForCurrentScheme(assetName: String): Boolean =
    assetName.contains(if (BuildConfig.GLOBAL_SCHEME) "-global-" else "-scoped-")
