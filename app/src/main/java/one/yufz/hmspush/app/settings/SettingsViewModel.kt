package one.yufz.hmspush.app.settings

import android.content.ComponentName
import android.content.pm.PackageManager
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import one.yufz.hmspush.app.App
import one.yufz.hmspush.app.HmsPushClient
import one.yufz.hmspush.app.mainActivityAlias
import one.yufz.hmspush.common.HmsCoreUtil
import one.yufz.hmspush.common.model.PrefsModel

data class SettingsState(
    val preferences: PrefsModel = PrefsModel()
) : MavericksState

class SettingsViewModel(initialState: SettingsState) : MavericksViewModel<SettingsState>(initialState) {
    private val context = App.instance

    init {
        queryPreferences()
    }

    fun queryPreferences() {
        viewModelScope.launch(Dispatchers.IO) {
            val pref = HmsPushClient.preference
            setState { copy(preferences = pref) }
        }
    }

    fun updatePreference(updateAction: PrefsModel.() -> Unit) {
        setState {
            val copy = preferences.copy()
            updateAction(copy)
            viewModelScope.launch(Dispatchers.IO) {
                HmsPushClient.updatePreference(copy)
            }
            copy(preferences = copy)
        }
    }

    fun setHmsCoreForeground(foreground: Boolean) {
        HmsCoreUtil.startHmsCoreService(context, foreground)
    }

    fun toggleAppIcon(hide: Boolean) {
        updatePreference { hideAppIcon = hide }
        val newState = if (hide) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, mainActivityAlias),
            newState,
            PackageManager.DONT_KILL_APP
        )
    }
}