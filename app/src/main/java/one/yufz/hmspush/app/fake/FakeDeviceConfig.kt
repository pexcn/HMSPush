package one.yufz.hmspush.app.fake

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import one.yufz.hmspush.app.util.ShellUtil

typealias ConfigMap = Map<String, Pair<List<String>, Boolean>>

object FakeDeviceConfig {
    private const val TAG = "FakeDeviceConfig"

    private const val CONFIG_PATH = "/data/adb/hmspush/app.conf"

    private var _configMapFlow: MutableStateFlow<ConfigMap> = MutableStateFlow(emptyMap())
    val configMapFlow: StateFlow<ConfigMap> = _configMapFlow

    suspend fun loadConfig(): ConfigMap {
        val result = ShellUtil.executeCommand("su", "-c", "cat $CONFIG_PATH")
        if (result.isSuccess) {
            Log.d(TAG, "onSuccess, output=${result.output}")
            _configMapFlow.value = parseConfig(result.output)
        } else {
            Log.e(TAG, "loadConfig error, msg: ${result.output}")
        }
        return _configMapFlow.value
    }

    private fun parseConfig(lines: String): ConfigMap {
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
                val processes = it.value.mapNotNull { it.second }
                val skipBuild = it.value.firstOrNull()?.third ?: false
                Pair(processes, skipBuild)
            }

        Log.d(TAG, "parseConfig() returned: $configs")
        return configs
    }

    suspend fun update(packageName: String, processList: List<String>, skipBuild: Boolean = false) {
        val newMap = _configMapFlow.value.toMutableMap()
        newMap[packageName] = Pair(processList, skipBuild)
        _configMapFlow.value = newMap
        writeConfig()
    }

    suspend fun deleteConfig(packageName: String) {
        val newMap = _configMapFlow.value.toMutableMap()
        newMap.remove(packageName)
        _configMapFlow.value = newMap
        writeConfig()
    }

    suspend fun writeConfig() {
        val lines = _configMapFlow.value.entries
            .map { entry ->
                val pkg = if (entry.value.second) "!${entry.key}" else entry.key
                if (entry.value.first.isEmpty()) {
                    pkg
                } else {
                    entry.value.first.joinToString("\\n") { "$pkg|$it" }
                }
            }
            .joinToString("\\n") { it }

        val result = ShellUtil.executeCommand("su", "-c", "mkdir -p \$(dirname $CONFIG_PATH) && echo '$lines' > $CONFIG_PATH")

        if (result.isSuccess) {
            Log.d(TAG, "writeConfig output=${result.output}")
        } else {
            Log.e(TAG, "writeConfig error output=${result.output}")
        }
    }
}