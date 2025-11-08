package com.xalitoria.referrals.velocity

import com.google.common.io.ByteStreams.newDataOutput
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.ChannelIdentifier
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import org.slf4j.Logger
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Suppress("UnstableApiUsage")
@Plugin(
    id = "xalirefer",
    name = "XaliRefer-Velocity"
)
class XaliRefer @Inject constructor(private val server: ProxyServer, private val logger: Logger) {

    lateinit var instance: PluginContainer
    lateinit var luckPerms: LuckPerms
        private set

    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        instance = server.pluginManager.fromInstance(this).get()

        logger.info("Registering server channel: $PLUGIN_CHANNEL")
        server.channelRegistrar.register(pluginChannelIdentifier)
        logger.info("Channel registered successfully!")

        val name = instance.description.name.get()

        if (!server.pluginManager.getPlugin("luckperms").isPresent) {
            logger.warn("LuckPerms plugin not found! Core functionality of $name will not be enabled.")
            return
        }
        try {
            luckPerms = LuckPermsProvider.get()
            logger.info("LuckPerms found. $name is fully enabled.")
        } catch (ex: Exception) {
            logger.error("Failed to get LuckPerms API. Disabling core functionality", ex)
            return
        }
    }

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (event.identifier != pluginChannelIdentifier) return

        val source = event.source as? ServerConnection
        if (source == null) {
            logger.warn("Plugin message received from non-player source: $source")
            return
        }

        val input = event.dataAsDataStream()

        try {
            val subChannel = input.readUTF()
            logger.info("Received message: $subChannel")
            when (subChannel) {
                "ReferralRequest" -> {
                    val playerId = input.readUTF()
                    logger.info("Processing referral request for UUID: $playerId")

                    val player = server.getPlayer(UUID.fromString(playerId)).get()
                    val vhost = player.virtualHost.getOrNull()
                    val domain = if (vhost != null) vhost.hostString else player.remoteAddress.address.hostAddress
                    logger.info(player.username + " has joined with IP: " + domain)

                    val output = newDataOutput()
                    output.writeUTF("ReferralResponse")
                    output.writeUTF(domain)
                    source.sendPluginMessage(pluginChannelIdentifier, output.toByteArray())
                }
                else -> logger.warn("Unknown sub-channel: $subChannel")
            }
        } catch (ex: Exception) {
            logger.error("Error processing plugin message", ex)
        }
    }

    companion object {
        const val PLUGIN_CHANNEL = "xalirefer:message"
        private val pluginChannelIdentifier: ChannelIdentifier = MinecraftChannelIdentifier.from(PLUGIN_CHANNEL)
    }
}