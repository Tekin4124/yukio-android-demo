package com.example.test.update

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

internal enum class InAppUpdateUiState {
    IDLE,
    READY_TO_INSTALL
}

/**
 * Coordinates Google Play's flexible update flow.
 *
 * The Play consent UI is shown at most once per available version in a
 * 24-hour window. After download, Compose displays a restart confirmation.
 */
internal class PlayInAppUpdateController(
    private val activity: ComponentActivity,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity)
) {
    private val preferences = activity.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val _uiState = MutableStateFlow(InAppUpdateUiState.IDLE)
    val uiState: StateFlow<InAppUpdateUiState> = _uiState.asStateFlow()

    private var started = false
    private var requestInFlight = false
    private var downloadedPromptDismissed = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> showDownloadedPromptIfAllowed()
            InstallStatus.CANCELED,
            InstallStatus.FAILED,
            InstallStatus.INSTALLED -> _uiState.value = InAppUpdateUiState.IDLE
        }
    }

    fun start() {
        if (started) return
        started = true
        appUpdateManager.registerListener(installStateListener)
        checkForUpdate()
    }

    fun onResume() {
        if (started) checkForUpdate()
    }

    fun close() {
        if (!started) return
        appUpdateManager.unregisterListener(installStateListener)
        started = false
    }

    fun completeDownloadedUpdate() {
        downloadedPromptDismissed = false
        appUpdateManager.completeUpdate()
    }

    fun dismissDownloadedPrompt() {
        downloadedPromptDismissed = true
        _uiState.value = InAppUpdateUiState.IDLE
    }

    private fun checkForUpdate() {
        if (requestInFlight) return
        requestInFlight = true

        val updateInfoTask = appUpdateManager.appUpdateInfo
        updateInfoTask.addOnSuccessListener { updateInfo ->
            when {
                updateInfo.installStatus() == InstallStatus.DOWNLOADED -> {
                    showDownloadedPromptIfAllowed()
                }
                updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    updateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) &&
                    shouldPrompt(updateInfo.availableVersionCode()) -> {
                    val updateStarted = runCatching {
                        appUpdateManager.startUpdateFlowForResult(
                            updateInfo,
                            updateLauncher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    }.onFailure { error ->
                        Timber.w(error, "Google Play uygulama güncellemesi başlatılamadı")
                    }.getOrDefault(false)

                    if (updateStarted) {
                        recordPrompt(updateInfo.availableVersionCode())
                    }
                }
            }
        }
        updateInfoTask.addOnFailureListener { error ->
            // Sideload/debug installations and devices without Play Store can
            // legitimately reach this path. App startup must continue.
            Timber.d(error, "Google Play güncelleme bilgisi alınamadı")
        }
        updateInfoTask.addOnCompleteListener {
            requestInFlight = false
        }
    }

    private fun showDownloadedPromptIfAllowed() {
        if (!downloadedPromptDismissed) {
            _uiState.value = InAppUpdateUiState.READY_TO_INSTALL
        }
    }

    private fun shouldPrompt(availableVersionCode: Int): Boolean {
        val previousVersion = preferences.getInt(KEY_VERSION_CODE, 0)
        val previousPromptAt = preferences.getLong(KEY_PROMPTED_AT, 0L)
        val previous = if (previousVersion > 0 && previousPromptAt > 0L) {
            InAppUpdatePromptRecord(previousVersion, previousPromptAt)
        } else {
            null
        }
        return InAppUpdatePromptPolicy.shouldPrompt(
            availableVersionCode = availableVersionCode,
            previous = previous,
            nowMillis = nowMillis()
        )
    }

    private fun recordPrompt(availableVersionCode: Int) {
        preferences.edit()
            .putInt(KEY_VERSION_CODE, availableVersionCode)
            .putLong(KEY_PROMPTED_AT, nowMillis())
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "play_in_app_update"
        const val KEY_VERSION_CODE = "prompted_version_code"
        const val KEY_PROMPTED_AT = "prompted_at_millis"
    }
}
