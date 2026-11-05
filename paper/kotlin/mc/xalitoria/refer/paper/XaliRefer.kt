package mc.xalitoria.refer.paper

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mc.xalitoria.refer.paper.command.ReferCommand
import mc.xalitoria.refer.paper.data.config.MainConfig
import mc.xalitoria.refer.paper.data.config.MessageConfig
import mc.xalitoria.refer.paper.data.constants.Permissions
import mc.xalitoria.refer.paper.data.db.DatabaseManager
import mc.xalitoria.refer.paper.external.ReferExpansion
import mc.xalitoria.refer.paper.listener.PlayerListener
import mc.xalitoria.refer.paper.playtime.ActivityListener
import mc.xalitoria.refer.paper.proxy.ProxyLink
import mc.xalitoria.refer.paper.service.ReferralService
import mc.xalitoria.util.config.YamlFile
import mc.xalitoria.util.message.*
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import org.bukkit.plugin.java.JavaPlugin

class XaliRefer : JavaPlugin() {
    lateinit var config: YamlFile<MainConfig>
        private set

    lateinit var messageConfig: YamlFile<MessageConfig>
        private set

    lateinit var scope: CoroutineScope
        private set

    lateinit var service: ReferralService
        private set

    private var link: ProxyLink? = null
    private var expansion: ReferExpansion? = null

    override fun onEnable() {
        scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                logger.fail("Unhandled coroutine failure", throwable)
            }
        )

        try {
            config = loadConfig()
            messageConfig = loadMessages(config.data)
        } catch (e: Exception) {
            logger.fail("The configuration is wrong; disabling.", e)
            server.pluginManager.disablePlugin(this)
            return
        }
        applyDebug()

        val pm = server.pluginManager
        try {
            DatabaseManager.connect(this, config.data.database)
        } catch (e: Exception) {
            logger.fail("Failed to connect to the database; disabling.", e)
            pm.disablePlugin(this)
            return
        }
        debug("Connected to the {0} database", config.data.database.type)

        // Everyone may see their referrals unless a permissions plugin says otherwise.
        pm.addPermission(Permission(Permissions.USE, PermissionDefault.TRUE))
        pm.addPermission(Permission(Permissions.LIST_OTHERS, PermissionDefault.OP))
        pm.addPermission(Permission(Permissions.ADMIN, PermissionDefault.OP))
        pm.addPermission(Permission(Permissions.LOG, PermissionDefault.OP))
        pm.addPermission(Permission(Permissions.REVIEW, PermissionDefault.OP))

        service = ReferralService(this)
        service.start()
        pm.registerEvents(PlayerListener(this), this)
        pm.registerEvents(ActivityListener(service.tracker), this)

        if (config.data.proxy.enabled) {
            val secret = ProxyLink.readSecret(this)
            if (secret.isNotEmpty()) {
                link = ProxyLink(this, secret).also { it.start() }
                logger.info("Velocity forwarding found: referral addresses are read from the proxy plugin")
            } else logger.warning(
                "proxy.enabled is on, but Velocity forwarding is not set up in config/paper-global.yml, so no referral address will " +
                    "arrive. Behind Velocity, turn on modern forwarding; without a proxy, set proxy.enabled to false."
            )
        }

        if (pm.isPluginEnabled("PlaceholderAPI")) {
            expansion = ReferExpansion(this).also { it.register() }
        }

        val command = ReferCommand(this)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registered = event.registrar().register(command.builder().build(), command.description, command.aliases)
            val lost = (listOf(command.label) + command.aliases).filter { it !in registered }
            if (lost.isNotEmpty()) logger.warning("/${lost.joinToString(", /")} is used by another command; use /${command.label} or /${pluginMeta.name.lowercase()}:${command.label}.")
        }
        debug("Enabled")
    }

    override fun onDisable() {
        expansion?.unregister()
        link?.stop()
        if (::service.isInitialized) {
            // Plugin threads are about to stop, so what the watched players earned is written now, with a bound on the wait.
            val sessions = service.tracker.stopAll()
            runBlocking {
                withTimeoutOrNull(SHUTDOWN_SAVE_MILLIS) { sessions.forEach { service.save(it) } }
                    ?: logger.warning("Saving the playtime of ${sessions.size} players took too long; the last minutes were not saved")
            }
        }
        if (::scope.isInitialized) scope.cancel("XaliRefer is disabling")
        DatabaseManager.close()
    }

    /**
     * Reads the configuration and the language file again. If either is wrong the old ones stay in use and this throws,
     * saying what is wrong.
     */
    fun reload() {
        val newConfig = loadConfig()
        val newMessages = loadMessages(newConfig.data)
        config = newConfig
        messageConfig = newMessages
        applyDebug()
        service.configure()
        debug("Reloaded")
    }

    private fun loadConfig(): YamlFile<MainConfig> {
        val file = YamlFile(dataPath.resolve("config.yml"), MainConfig.serializer(), ::MainConfig)
        file.load()
        file.data.validate()
        // Written again so a setting added by an update shows up in the file.
        file.save()
        return file
    }

    private fun loadMessages(config: MainConfig): YamlFile<MessageConfig> {
        val file = YamlFile(dataPath.resolve("language", config.language + ".yml"), MessageConfig.serializer(), ::MessageConfig)
        file.load()
        file.save()
        return file
    }

    /** Starts from `debug` in the configuration. */
    private fun applyDebug() {
        debugOn = config.data.debug
        updateDebugger()
    }

    /** Turns the debug log on or off until the next reload, and returns whether it is on. */
    fun toggleDebug(): Boolean {
        debugOn = !debugOn
        updateDebugger()
        return debugOn
    }

    private fun updateDebugger() {
        debugger =
            if (debugOn) { message: String, args: Array<out Any?> -> logger.info(message.formatArgs(*args)) }
            else { _: String, _: Array<out Any?> -> }
    }

    private var debugOn = false

    companion object {
        private const val SHUTDOWN_SAVE_MILLIS = 5_000L
        private var debugger: (String, Array<out Any?>) -> Unit = { _, _ -> }
        fun debug(message: String, vararg args: Any?) = debugger(message, args)
    }
}
