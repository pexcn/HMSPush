package one.yufz.hmspush.app.icon

import com.airbnb.mvrx.MavericksState
import com.airbnb.mvrx.MavericksViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.yufz.hmspush.R
import one.yufz.hmspush.app.App
import one.yufz.hmspush.app.HmsPushClient
import one.yufz.hmspush.common.IconData
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

data class IconState(
    val icons: List<IconData> = emptyList(),
    val filterKeywords: String = "",
    val importState: IconViewModel.ImportState = IconViewModel.ImportState(false)
) : MavericksState {
    val filteredIcons: List<IconData>
        get() = if (filterKeywords.isEmpty()) icons else icons.filter {
            it.appName.contains(filterKeywords, true) || it.packageName.contains(filterKeywords, true)
        }
}

class IconViewModel(initialState: IconState) : MavericksViewModel<IconState>(initialState) {
    companion object {
        private const val TAG = "IconViewModel"
        const val ICON_URL = "https://raw.githubusercontent.com/fankes/AndroidNotifyIconAdapt/main/APP/NotifyIconsSupportConfig.json"
    }

    data class ImportState(val loading: Boolean, val info: String? = null)

    private val app = App.instance

    init {
        loadIcon()
    }

    fun fetchIconFromUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            setState { copy(importState = ImportState(true)) }

            try {
                readIconFromUrl(url).forEach {
                    HmsPushClient.saveIcon(it)
                }
            } catch (e: Throwable) {
                setState { copy(importState = ImportState(false, e.message)) }
                return@launch
            }

            setState { copy(importState = ImportState(false, app.getString(R.string.import_complete))) }

            loadIcon()
        }
    }

    fun loadIcon() {
        viewModelScope.launch(Dispatchers.IO) {
            val icons = HmsPushClient.allIcon.mapNotNull { it.toIconData() }
            setState { copy(icons = icons) }
        }
    }

    private suspend fun readIconFromUrl(url: String): List<IconData> {
        return withContext(Dispatchers.IO) {
            val jsonString = URL(url).readText()
            val jsonArray = JSONArray(jsonString)
            val iconList = ArrayList<IconData>(jsonArray.length())

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.get(i) as JSONObject
                iconList.add(IconData.fromJson(obj))
            }
            iconList
        }
    }

    fun cancelImport() {
        setState { copy(importState = ImportState(false)) }
    }

    fun filter(keywords: String) {
        setState { copy(filterKeywords = keywords) }
    }

    fun clearIcons() {
        viewModelScope.launch(Dispatchers.IO) {
            HmsPushClient.deleteIcon()
            loadIcon()
        }
    }

    fun deleteIcon(vararg packageName: String) {
        viewModelScope.launch {
            val set = packageName.toHashSet()
            setState { copy(icons = icons.filterNot { it.packageName in set }) }

            HmsPushClient.deleteIcon(*packageName)
        }
    }
}