package com.xalitoria.referrals.spigot

import com.xalitoria.referrals.spigot.api.XaliReferAPI as api
import com.xalitoria.referrals.spigot.config.MainConfig
import com.xalitoria.referrals.spigot.config.MessageConfig
import com.xalitoria.referrals.spigot.tracking.afk.AfkEvents
import com.xalitoria.referrals.spigot.tracking.ReferProxyListener
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import org.bukkit.Bukkit.getConsoleSender
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.*

typealias PlayerID = UUID
typealias ReferID = UUID

@Suppress("NOTHING_TO_INLINE")
class XaliRefer : JavaPlugin() {

    override fun onEnable() {
        instance = this

        MainConfig.load()
        MessageConfig.load()

        verifyPlugin("PlaceholderAPI") {
            Placeholders(instance).register()
        }

        verifyPlugin("LuckPerms") {
            luckPerms = LuckPermsProvider.get()
        }

        server.pluginManager.registerEvents(AfkEvents(), instance)
        getCommand("xalireferral")!!.setExecutor(Command())

        logger.info("Registering server channel: $PLUGIN_CHANNEL")

        server.messenger.run {
            registerOutgoingPluginChannel(instance, "BungeeCord")
            registerOutgoingPluginChannel(instance, PLUGIN_CHANNEL)
            registerIncomingPluginChannel(instance, PLUGIN_CHANNEL, ReferProxyListener())
        }
        api.refreshTopReferrals()
    }

    override fun onDisable() {
        MessageConfig.PREFIX.message(getConsoleSender(), null) { "$this &cDisabling..." }
        api.save()
        api.stopRefreshTopReferrals()
        MessageConfig.PREFIX.message(getConsoleSender(), null) { "$this &4Disabled!" }
    }

    fun reload() {
        MainConfig.load()
        MessageConfig.load()
        api.save()
        api.refreshTopReferrals()
    }

    private fun verifyPlugin(pluginName: String, block: () -> Unit) {
        if (server.pluginManager.isPluginEnabled(pluginName)) {
            block()
            MessageConfig.PREFIX.message(getConsoleSender(), null) { "$this $pluginName was found!" }
            return
        }
        MessageConfig.PREFIX.message(getConsoleSender(), null) { "$this $pluginName was not found! &cDisabling..." }
        server.pluginManager.disablePlugin(instance)
    }

    companion object {
        internal lateinit var instance: XaliRefer
            private set

        lateinit var luckPerms: LuckPerms
            private set

        val logger = instance.logger

        const val PLUGIN_CHANNEL = "xalirefer:message"

        fun runTask(task: () -> Unit): BukkitTask =
            instance.server.scheduler.runTask(instance, Runnable(task))

        fun runTaskLater(delayTicks: Long, task: () -> Unit): BukkitTask =
            instance.server.scheduler.runTaskLater(instance, Runnable(task), delayTicks)

        fun runTaskAsync(task: () -> Unit): BukkitTask =
            instance.server.scheduler.runTaskAsynchronously(instance, Runnable(task))

        fun runTaskRepeat(delayTicks: Long, period: Long, task: () -> Unit): BukkitTask =
            instance.server.scheduler.runTaskTimer(instance, Runnable(task), delayTicks, period)
    }
}
