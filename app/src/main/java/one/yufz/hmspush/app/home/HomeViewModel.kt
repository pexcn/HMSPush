package one.yufz.hmspush.app.home

import android.content.Intent
import android.content.pm.PackageManager
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import one.yufz.hmspush.R
import one.yufz.hmspush.app.App
import one.yufz.hmspush.app.HmsPushClient
import one.yufz.hmspush.app.util.registerPackageChangeFlow
import one.yufz.hmspush.common.API_VERSION
import one.yufz.hmspush.common.HMS_PACKAGE_NAME

data class HomeState(
    val usable: Boolean = false,
    val tips: String = "",
    val reason: HomeViewModel.Reason = HomeViewModel.Reason.Checking,
    val searching: Boolean = false,
    val searchText: String = ""
) : MavericksState

class HomeViewModel(initialState: HomeState) : MavericksViewModel<HomeState>(initialState) {
    enum class Reason {
        None,
        Checking,
        HmsCoreNotInstalled,
        HmsCoreNotActivated,
        HmsPushVersionNotMatch
    }

    private val app = App.instance
    private var registerJob: Job? = null

    init {
        app.registerPackageChangeFlow()
            .filter { it.dataString?.removePrefix("package:") == HMS_PACKAGE_NAME }
            .onEach { onHmsPackageChanged(it) }
            .launchIn(viewModelScope)

        registerServiceChange()
    }

    private fun onHmsPackageChanged(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> registerServiceChange()
            Intent.ACTION_PACKAGE_REMOVED -> registerJob?.cancel()
        }
        checkHmsCore()
    }

    private fun registerServiceChange() {
        registerJob?.cancel()
        registerJob = HmsPushClient.getHmsPushServiceFlow()
            .onEach { checkHmsCore() }
            .launchIn(viewModelScope)
    }

    fun setSearching(searching: Boolean) {
        setState { copy(searching = searching) }
    }

    fun checkHmsCore() {
        try {
            app.packageManager.getApplicationInfo(HMS_PACKAGE_NAME, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            setState { copy(usable = false, tips = app.getString(R.string.hms_core_not_found), reason = Reason.HmsCoreNotInstalled) }
            return
        }

        val moduleVersion = HmsPushClient.moduleVersion
        if (moduleVersion == null) {
            setState { copy(usable = false, tips = app.getString(R.string.hms_not_activated), reason = Reason.HmsCoreNotActivated) }
            return
        }

        if (moduleVersion.apiVersion != API_VERSION) {
            setState { copy(usable = false, tips = app.getString(R.string.hms_version_not_match), reason = Reason.HmsPushVersionNotMatch) }
            return
        }

        setState { copy(usable = true, tips = "", reason = Reason.None) }
    }

    fun setSearchText(text: String) {
        setState { copy(searchText = text) }
    }
}