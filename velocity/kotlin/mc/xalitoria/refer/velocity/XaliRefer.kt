package mc.xalitoria.refer.velocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import mc.xalitoria.refer.protocol.Joined
import mc.xalitoria.refer.protocol.Protocol
import org.slf4j.Logger
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText

/**
 * The proxy end of XaliRefer. The proxy is the only one that knows which address a player typed to connect (the servers
 * behind it see the proxy's), so when a player comes onto the network it tells the server they land on, signed with the
 * forwarding secret from velocity.toml. The server decides what the address means.
 *
 * It tells a server once per visit to the network, not each time the player changes server.
 */
class XaliRefer(
    private val proxy: ProxyServer,
    private val logger: Logger,
    private val dataDirectory: Path,
    /** The loader Velocity knows as the plugin, which listeners are registered with. */
    private val plugin: Any,
) : Runnable, AutoCloseable {
    private val channel = MinecraftChannelIdentifier.from(Protocol.CHANNEL)

    /**
     * The forwarding secret, found the way Velocity finds it: `VELOCITY_FORWARDING_SECRET`, else the file named by
     * `forwarding-secret-file` in velocity.toml (`forwarding.secret` when unset). The API doesn't expose it.
     */
    private var secret: String? = null

    override fun run() {
        dataDirectory.createDirectories()
        val root = dataDirectory.toAbsolutePath().parent.parent // plugins/<id> sits under the folder holding velocity.toml
        secret = runCatching { System.getenv("VELOCITY_FORWARDING_SECRET") }.getOrNull()
        if (secret == null) secret = try {
            val file = secretFileRegex
                .find(root.resolve("velocity.toml").readText())
                ?.groupValues?.getOrNull(1)
                ?: "forwarding.secret"
            root.resolve(file).readText().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            logger.error("Could not read the forwarding secret", e)
            null
        }
        if (secret == null) logger.error("Could not read the forwarding secret, so no referral will be relayed")

        proxy.channelRegistrar.register(channel)
        proxy.eventManager.register(plugin, this)
        logger.info("XaliRefer relay ready on {}", Protocol.CHANNEL)
    }

    override fun close() {
        proxy.channelRegistrar.unregister(channel)
    }

    @Subscribe
    fun onServerPostConnect(event: ServerPostConnectEvent) {
        // Only the first server of a visit: a player passing between servers typed the address once.
        if (event.previousServer != null) return
        val secret = secret ?: return
        val player = event.player
        val host = player.virtualHost.map { it.hostString.lowercase().trimEnd('.') }.orElse(null)
        if (host.isNullOrEmpty() || host.length > Protocol.MAX_HOST) return
        val server = player.currentServer.orElse(null) ?: return
        server.sendPluginMessage(channel, Protocol.seal(secret, Joined(player.uniqueId, host)))
    }

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (event.identifier != channel) return
        // A client can send on any channel, and what it sends here would be taken as the proxy's own by the server
        // behind it, so nothing a player sends here goes anywhere. The secret would stop a forgery anyway.
        event.result = PluginMessageEvent.ForwardResult.handled()
    }

    companion object {
        private val secretFileRegex = Regex("""(?m)^\s*forwarding-secret-file\s*=\s*"([^"]*)"""")
    }
}
