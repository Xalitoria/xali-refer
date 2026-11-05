import mc.xalitoria.refer.paper.data.config.MainConfig
import mc.xalitoria.refer.paper.data.config.MessageConfig
import mc.xalitoria.refer.paper.playtime.Rules
import mc.xalitoria.util.config.DatabaseType
import mc.xalitoria.util.config.YamlFile
import mc.xalitoria.util.config.databaseType
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigFile {
    private fun MainConfig.withPlaytime(playtime: MainConfig.Playtime) = copy(playtime = playtime)

    @Test
    fun `the defaults are valid and read as times`() {
        val config = MainConfig()
        config.validate()
        assertEquals(7200, config.playtime.requirement().inWholeSeconds)
        assertEquals(30, config.playtime.afkThreshold().inWholeSeconds)
        assertEquals(86_400, config.referral.window().inWholeSeconds)
        assertEquals(DatabaseType.SQLITE, config.database.databaseType)
        assertEquals("xalirefer_", config.database.tablePrefix)
        val rules = Rules.of(config.playtime)
        assertEquals(30_000, rules.afkThreshold)
        assertEquals(120_000, rules.window)
    }

    @Test
    fun `times may be mixed units`() {
        val config = MainConfig(playtime = MainConfig.Playtime(requirement = "1h 30m"))
        assertEquals(5400, config.playtime.requirement().inWholeSeconds)
        assertEquals(0, MainConfig(referral = MainConfig.Referral(window = "0s")).referral.window().inWholeSeconds)
    }

    @Test
    fun `a wrong time names the setting`() {
        val bad = MainConfig().withPlaytime(MainConfig.Playtime(afkThreshold = "half a minute"))
        val error = assertFailsWith<IllegalArgumentException> { bad.validate() }
        assertTrue("playtime.afk-threshold" in error.message!!, error.message)
        assertFailsWith<IllegalArgumentException> { MainConfig().withPlaytime(MainConfig.Playtime(requirement = "-5m")).validate() }
        assertFailsWith<IllegalArgumentException> { MainConfig().withPlaytime(MainConfig.Playtime(requirement = "0s")).validate() }
    }

    @Test
    fun `a check can't be rarer than the AFK threshold`() {
        val error = assertFailsWith<IllegalArgumentException> {
            MainConfig().withPlaytime(MainConfig.Playtime(afkThreshold = "10s", checkInterval = "1m")).validate()
        }
        assertTrue("check-interval" in error.message!!, error.message)
    }

    @Test
    fun `the machine settings are bounded`() {
        val machines = MainConfig.Playtime.Machines()
        assertFailsWith<IllegalArgumentException> { MainConfig().withPlaytime(MainConfig.Playtime(machines = machines.copy(radius = 0))).validate() }
        assertFailsWith<IllegalArgumentException> { MainConfig().withPlaytime(MainConfig.Playtime(machines = machines.copy(minVariety = 99))).validate() }
    }

    @Test
    fun `a file is written with the defaults and read back`() {
        val dir = Files.createTempDirectory("xalirefer-config")
        val file = YamlFile(dir.resolve("config.yml"), MainConfig.serializer(), ::MainConfig)
        file.load()
        val text = Files.readString(dir.resolve("config.yml"))
        assertTrue("table-prefix: \"xalirefer_\"" in text || "table-prefix: xalirefer_" in text, text)
        assertTrue("afk-threshold" in text, text)

        Files.writeString(dir.resolve("config.yml"), text.replace("requirement: \"2h\"", "requirement: \"3h\"").replace("requirement: 2h", "requirement: 3h"))
        val again = YamlFile(dir.resolve("config.yml"), MainConfig.serializer(), ::MainConfig)
        again.load()
        assertEquals(10_800, again.data.playtime.requirement().inWholeSeconds)

        val messages = YamlFile(dir.resolve("en-us.yml"), MessageConfig.serializer(), ::MessageConfig)
        messages.load()
        assertEquals("1 hour 5 minutes", messages.data.words.duration(3900))
    }

    @Test
    fun `times are written in words`() {
        val words = MessageConfig.Words()
        assertEquals("0 seconds", words.duration(0))
        assertEquals("1 second", words.duration(1))
        assertEquals("2 hours", words.duration(7200))
        assertEquals("1 day 1 hour 1 minute 1 second", words.duration(90_061))
        assertEquals("0 seconds", words.duration(-5))
    }
}
