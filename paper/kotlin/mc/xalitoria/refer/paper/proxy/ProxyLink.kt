package mc.xalitoria.refer.paper.proxy

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import mc.xalitoria.refer.paper.XaliRefer
import mc.xalitoria.refer.paper.XaliRefer.Companion.debug
import mc.xalitoria.refer.protocol.Protocol
import mc.xalitoria.util.message.fail
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import kotlin.io.path.readText

/**
 * This server's end of the proxy relay (the Velocity plugin): the proxy tells the server which address a player joined
 * with, since the connection from the proxy only shows the proxy's own.
 *
 * Messages are signed with Velocity's forwarding secret, which Paper keeps in `config/paper-global.yml` (`proxies.velocity`).
 * Without it nothing sent here can be trusted, so there is no link.
 */
class ProxyLink(private val plugin: XaliRefer, private val secret: String) : PluginMessageListener {
    fun start() {
        plugin.server.messenger.registerIncomingPluginChannel(plugin, Protocol.CHANNEL, this)
    }

    fun stop() {
        plugin.server.messenger.unregisterIncomingPluginChannel(plugin, Protocol.CHANNEL, this)
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != Protocol.CHANNEL) return
        // Anything a client can send to a plugin channel reaches here too, so only a signed message is acted on.
        val joined = runCatching { Protocol.open(secret, message) }.getOrNull()
        if (joined == null) {
            debug("Ignored a message from {0} with a missing or wrong signature", player.name)
            return
        }
        // It reaches the server on the connection of the player it is about; anything else is not the proxy's doing.
        if (joined.player != player.uniqueId) return
        val firstPlayed = player.firstPlayed
        plugin.scope.launch {
            try {
                plugin.service.refer(player, joined.host, firstPlayed)
            } catch (e: Exception) {
                plugin.logger.fail("Failed to handle the referral of ${player.name}", e)
            }
        }
    }

    companion object {
        /**
         * Velocity's forwarding secret from Paper's own `config/paper-global.yml`, the one it shares with the proxy;
         * empty unless Velocity forwarding is enabled. Paper only reads it at startup, so this does too.
         */
        fun readSecret(plugin: XaliRefer): String = try {
            Yaml(configuration = YamlConfiguration(strictMode = false))
                .decodeFromString(PaperGlobal.serializer(), plugin.server.pluginsFolder.toPath().resolveSibling("config/paper-global.yml").readText())
                .proxies.velocity.takeIf { it.enabled }?.secret.orEmpty()
        } catch (e: Exception) {
            debug("Could not read the Velocity secret from paper-global.yml: {0}", e)
            ""
        }

        @Serializable
        private class PaperGlobal(val proxies: Proxies = Proxies()) {
            @Serializable
            class Proxies(val velocity: Velocity = Velocity())

            @Serializable
            class Velocity(val enabled: Boolean = false, val secret: String = "")
        }
    }
}
