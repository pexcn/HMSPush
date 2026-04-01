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

typealias ConfigMap = Map<String, Pair<List<String>, Boolean>>

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

            //split by | and map to Triple(packageName,process,skipBuild)
            .map {
                val split = it.split("|")
                val raw = split.get(0)
                val skipBuild = raw.startsWith("!")
                val packageName = if (skipBuild) raw.removePrefix("!") else raw
                val process = split.getOrNull(1)
                Triple(packageName, process, skipBuild)
            }

            //group by packageName
            .groupBy { it.first }

            //map to Config
            .mapValues {
                val processes = it.value.mapNotNull { it.second }.filter { it.isNotBlank() }
                val skipBuild = it.value.firstOrNull()?.third ?: false
                Pair(processes, skipBuild)
            }
        Log.d(TAG, "parseConfig() returned: $configs")
        return configs
    }

    suspend fun update(packageName: String, processList: List<String>, skipBuild: Boolean = false): Result<Unit> {
        val newMap = _configMapFlow.value.toMutableMap()
        newMap[packageName] = Pair(processList, skipBuild)
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
                val pkg = if (entry.value.second) "!${entry.key}" else entry.key
                if (entry.value.first.isEmpty()) {
                    listOf(pkg)
                } else {
                    entry.value.first.map { "$pkg|$it" }
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