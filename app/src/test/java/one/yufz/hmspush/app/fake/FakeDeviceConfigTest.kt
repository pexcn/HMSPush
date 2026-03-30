package one.yufz.hmspush.app.fake

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeDeviceConfigTest {

    private class MockConfigStore : ConfigStore {
        var content: String = ""
        var success: Boolean = true

        override suspend fun loadConfig(): Result<String> {
            return if (success) Result.success(content) else Result.failure(Exception("Load failed"))
        }

        override suspend fun saveConfig(content: String): Result<Unit> {
            return if (success) {
                this.content = content
                Result.success(Unit)
            } else {
                Result.failure(Exception("Save failed"))
            }
        }
    }

    private lateinit var mockConfigStore: MockConfigStore

    @Before
    fun setUp() {
        mockConfigStore = MockConfigStore()
        FakeDeviceConfig.configStore = mockConfigStore
        FakeDeviceConfig.reset()
    }

    @Test
    fun testParseConfig() {
        val config = """
            # comment
            pkg1|pkg1:process1
            pkg1|process2
            pkg2
            
            pkg3|
        """.trimIndent()

        val map = FakeDeviceConfig.parseConfig(config)
        assertEquals(listOf("pkg1:process1", "process2"), map["pkg1"])
        assertEquals(emptyList<String>(), map["pkg2"])
        assertEquals(emptyList<String>(), map["pkg3"])
    }

    @Test
    fun testSerializeConfig() {
        val map: ConfigMap = mapOf(
            "pkg1" to listOf("pkg1:process1", "process2"),
            "pkg2" to emptyList(),
            "pkg3" to listOf("process3")
        )

        val serialized = FakeDeviceConfig.serializeConfig(map)
        val actualLines = serialized.lines().sorted()
        val expectedLines = listOf(
            "pkg1|pkg1:process1",
            "pkg1|process2",
            "pkg2",
            "pkg3|process3"
        ).sorted()
        assertEquals(expectedLines, actualLines)
    }

    @Test
    fun testUpdateAndDelete() = runBlocking {
        // Clear the state
        mockConfigStore.content = ""
        FakeDeviceConfig.loadConfig()
        
        FakeDeviceConfig.update("pkg1", listOf("p1"))
        assertEquals("pkg1|p1", mockConfigStore.content)

        FakeDeviceConfig.update("pkg2", emptyList())
        val actualLines = mockConfigStore.content.lines().filter { it.isNotBlank() }.sorted()
        val expectedLines = listOf("pkg1|p1", "pkg2").sorted()
        assertEquals("Update failed. Expected: $expectedLines, Actual: $actualLines", expectedLines, actualLines)

        FakeDeviceConfig.deleteConfig("pkg1")
        assertEquals("pkg2", mockConfigStore.content.trim())
    }
}
