package one.yufz.hmspush.app.fake

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import one.yufz.hmspush.app.util.ShellUtil
import one.yufz.hmspush.app.util.SystemPropertiesUtil

interface ConfigStore {
    suspend fun loadConfig(): String
    suspend fun saveConfig(content: String): Boolean
}

class ZygiskConfigStore(private val configPath: String) : ConfigStore {
    override suspend fun loadConfig(): String {
        val result = ShellUtil.executeCommand("su", "-c", "cat $configPath")
        return if (result.isSuccess) result.output else ""
    }

    override suspend fun saveConfig(content: String): Boolean {
        val result = ShellUtil.executeCommand("su", "-c", "mkdir -p \$(dirname $configPath) && echo '$content' > $configPath")
        return result.isSuccess
    }
}

typealias ConfigMap = Map<String, List<String>>

object FakeDeviceConfig {
    private const val TAG = "FakeDeviceConfig"

    val zygiskEnabled = SystemPropertiesUtil.get("hmspush.zygisk.enabled") == true.toString()

    private const val CONFIG_PATH = "/data/adb/hmspush/app.conf"

    var configStore: ConfigStore = ZygiskConfigStore(CONFIG_PATH)

    private var _configMapFlow: MutableStateFlow<ConfigMap> = MutableStateFlow(emptyMap())
    val configMapFlow: StateFlow<ConfigMap> = _configMapFlow

    suspend fun loadConfig(): ConfigMap {
        val content = configStore.loadConfig()
        _configMapFlow.value = parseConfig(content)
        return _configMapFlow.value
    }

    fun parseConfig(lines: String): ConfigMap {
        val configs = lines.lines()
            //ignore comment and blank line
            .filterNot { it.startsWith("#") || it.isBlank() }

            //split by | and map to Pair(packageName,process)
            .map {
                val split = it.split("|")
                val packageName = split.get(0)
                val process = split.getOrNull(1)
                Pair(packageName, process)
            }

            //group by packageName
            .groupBy { it.first }

            //map to Config
            .mapValues {
                it.value.mapNotNull { it.second }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() } ?: emptyList()
            }
        Log.d(TAG, "parseConfig() returned: $configs")
        return configs
    }

    suspend fun update(packageName: String, processList: List<String>) {
        val newMap = _configMapFlow.value.toMutableMap()
        newMap[packageName] = processList
        _configMapFlow.value = newMap
        writeConfig()
    }

    suspend fun deleteConfig(packageName: String) {
        val newMap = _configMapFlow.value.toMutableMap()
        newMap.remove(packageName)
        _configMapFlow.value = newMap
        writeConfig()
    }

    fun serializeConfig(configMap: ConfigMap): String {
        return configMap.entries
            .flatMap { entry ->
                if (entry.value.isEmpty()) {
                    listOf(entry.key)
                } else {
                    entry.value.map { "${entry.key}|$it" }
                }
            }
            .joinToString("\n")
    }

    suspend fun writeConfig() {
        val content = serializeConfig(_configMapFlow.value)
        val success = configStore.saveConfig(content)

        if (success) {
            Log.d(TAG, "writeConfig success")
        } else {
            Log.e(TAG, "writeConfig failed")
        }
    }

    fun reset() {
        _configMapFlow.value = emptyMap()
    }
}