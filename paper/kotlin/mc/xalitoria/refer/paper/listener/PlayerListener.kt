package mc.xalitoria.refer.paper.listener

import kotlinx.coroutines.launch
import mc.xalitoria.refer.paper.XaliRefer
import mc.xalitoria.refer.paper.XaliRefer.Companion.debug
import mc.xalitoria.util.message.fail
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

/**
 * Brings a player in and out: their row and the referral they are finishing, and, without a proxy plugin, the address
 * they connected with (a proxy plugin tells the server that itself, see [mc.xalitoria.refer.paper.proxy.ProxyLink]).
 */
class PlayerListener(private val plugin: XaliRefer) : Listener {
    /** The address each connecting player used, from login until they join. Few and short-lived; the oldest go when it fills. */
    private val hosts = object : LinkedHashMap<UUID, String>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, String>) = size > MAX_PENDING
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (plugin.config.data.proxy.enabled || event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
        synchronized(hosts) { hosts[event.uniqueId] = event.hostname }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        val host = synchronized(hosts) { hosts.remove(player.uniqueId) }
        val firstPlayed = player.firstPlayed
        val address = plugin.service.addressOf(player)
        plugin.scope.launch {
            try {
                plugin.service.arrive(player, address)
                if (host != null) plugin.service.refer(player, host, firstPlayed)
            } catch (e: Exception) {
                plugin.logger.fail("Failed to load the referral of ${player.name}", e)
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        synchronized(hosts) { hosts.remove(event.player.uniqueId) }
        debug("{0} left; watched: {1}", event.player.name, plugin.service.tracker.session(event.player.uniqueId) != null)
        plugin.service.leave(event.player)
    }

    companion object {
        private const val MAX_PENDING = 256
    }
}
