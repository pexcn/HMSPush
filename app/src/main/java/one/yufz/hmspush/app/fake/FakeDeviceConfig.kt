package one.yufz.hmspush.app.fake

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import one.yufz.hmspush.app.util.ShellUtil
import one.yufz.hmspush.app.util.SystemPropertiesUtil

interface ConfigStore {
    suspend fun loadConfig(): Result<String>
    suspend fun saveConfig(content: String): Result<Unit>
}

class ZygiskConfigStore(private val configPath: String) : ConfigStore {
    override suspend fun loadConfig(): Result<String> = ShellUtil.executeCommand("su", "-c", "cat $configPath")

    override suspend fun saveConfig(content: String): Result<Unit> =
        ShellUtil.executeCommand("su", "-c", "mkdir -p \$(dirname $configPath) && echo '$content' > $configPath").map { Unit }
}

typealias ConfigMap = Map<String, List<String>>

object FakeDeviceConfig {
    private const val TAG = "FakeDeviceConfig"

    val zygiskEnabled = SystemPropertiesUtil.get("hmspush.zygisk.enabled") == true.toString()

    private const val CONFIG_PATH = "/data/adb/hmspush/app.conf"

    var configStore: ConfigStore = ZygiskConfigStore(CONFIG_PATH)

    private var _configMapFlow: MutableStateFlow<ConfigMap> = MutableStateFlow(emptyMap())
    val configMapFlow: StateFlow<ConfigMap> = _configMapFlow

    suspend fun loadConfig(): Result<ConfigMap> {
        return configStore.loadConfig().map { content ->
            val configMap = parseConfig(content)
            _configMapFlow.value = configMap
            configMap
        }
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

    suspend fun update(packageName: String, processList: List<String>): Result<Unit> {
        val newMap = _configMapFlow.value.toMutableMap()
        newMap[packageName] = processList
        return writeConfig(newMap).onSuccess {
            _configMapFlow.value = newMap
        }
    }

    suspend fun deleteConfig(packageName: String): Result<Unit> {
        val newMap = _configMapFlow.value.toMutableMap()
        newMap.remove(packageName)
        return writeConfig(newMap).onSuccess {
            _configMapFlow.value = newMap
        }
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

    suspend fun writeConfig(config: ConfigMap): Result<Unit> {
        val content = serializeConfig(config)
        return configStore.saveConfig(content).onSuccess {
            Log.d(TAG, "writeConfig success")
        }.onFailure {
            Log.e(TAG, "writeConfig failed", it)
        }
    }

    fun reset() {
        _configMapFlow.value = emptyMap()
    }
}