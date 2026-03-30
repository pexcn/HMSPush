package one.yufz.hmspush.app.home

import android.content.pm.PackageManager
import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import one.yufz.hmspush.app.App
import one.yufz.hmspush.app.HmsPushClient
import one.yufz.hmspush.app.fake.ConfigMap
import one.yufz.hmspush.app.fake.FakeDeviceConfig
import one.yufz.hmspush.app.hms.SupportHmsAppList
import one.yufz.hmspush.common.model.PushHistoryModel
import one.yufz.hmspush.common.model.PushSignModel

data class AppListState(
    val appList: List<AppInfo> = emptyList(),
    val filterKeywords: String = ""
) : MavericksState {
    val filteredAppList: List<AppInfo>
        get() = if (filterKeywords.isEmpty()) appList else appList.filter {
            it.name.contains(filterKeywords, true) || it.packageName.contains(filterKeywords, true)
        }
}

class AppListViewModel(initialState: AppListState) : MavericksViewModel<AppListState>(initialState) {
    companion object {
        private const val TAG = "AppListViewModel"
    }

    private val context = App.instance

    private val supportedAppList = SupportHmsAppList(context)

    private val registeredListFlow = HmsPushClient.getPushSignFlow()

    private val historyListFlow = HmsPushClient.getPushHistoryFlow()

    init {
        viewModelScope.launch {
            FakeDeviceConfig.loadConfig()
            supportedAppList.init()
        }

        combine(supportedAppList.appListFlow, registeredListFlow, historyListFlow, FakeDeviceConfig.configMapFlow, ::mergeSource)
            .flowOn(Dispatchers.IO)
            .onEach { list -> setState { copy(appList = list) } }
            .launchIn(viewModelScope)
    }

    private fun mergeSource(appList: List<String>, registered: List<PushSignModel>, history: List<PushHistoryModel>, configMap: ConfigMap): List<AppInfo> {
        val pm = context.packageManager
        val registeredSet = registered.map { it.packageName }
        val historyMap = history.associateBy { it.packageName }
        return appList.map { packageName ->
            AppInfo(
                packageName = packageName,
                name = try {
                    pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName
                },
                registered = registeredSet.contains(packageName),
                lastPushTime = historyMap[packageName]?.pushTime,
                useZygiskFake = configMap.contains(packageName)
            )
        }
            .sortedWith(compareBy({ !it.registered }, { Long.MAX_VALUE - (it.lastPushTime ?: 0L) }))
    }

    fun filter(keywords: String) {
        setState { copy(filterKeywords = keywords) }
    }

    fun unregisterPush(packageName: String) {
        HmsPushClient.unregisterPush(packageName)
    }

    fun enableZygiskFake(packageName: String) {
        viewModelScope.launch {
            FakeDeviceConfig.update(packageName, emptyList())
        }
    }

    fun disableZygiskFake(packageName: String) {
        viewModelScope.launch {
            FakeDeviceConfig.deleteConfig(packageName)
        }
    }
}