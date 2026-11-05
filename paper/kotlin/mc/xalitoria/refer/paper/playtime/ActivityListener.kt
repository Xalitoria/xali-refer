package mc.xalitoria.refer.paper.playtime

import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInputEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.block.Action

/**
 * Tells the [PlaytimeTracker] what watched players do. It listens to the events that need a person (never to movement,
 * which is sent for every step of every player) and each handler returns at once for a player who isn't watched. They
 * run after other plugins, and what they cancel does not count, so a player pressing against a protected area isn't
 * building. Every handler is one map lookup and one volatile write.
 */
class ActivityListener(private val tracker: PlaytimeTracker) : Listener {
    private fun mark(player: Player, activity: Activity) {
        if (tracker.isEmpty) return
        tracker.mark(player.uniqueId, activity)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInput(event: PlayerInputEvent) = mark(event.player, Activity.INPUT)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChat(event: AsyncChatEvent) = mark(event.player, Activity.CHAT)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCommand(event: PlayerCommandPreprocessEvent) = mark(event.player, Activity.COMMAND)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) = mark(event.player, Activity.BLOCK)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) = mark(event.player, Activity.BLOCK)

    // Stepping on a pressure plate is the world's doing; a click is the player's.
    @EventHandler(priority = EventPriority.MONITOR)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.PHYSICAL) mark(event.player, Activity.INTERACT)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInteractEntity(event: PlayerInteractEntityEvent) = mark(event.player, Activity.INTERACT)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAttack(event: EntityDamageByEntityEvent) {
        (event.damager as? Player)?.let { mark(it, Activity.INTERACT) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventory(event: InventoryClickEvent) {
        (event.whoClicked as? Player)?.let { mark(it, Activity.INVENTORY) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) = mark(event.player, Activity.INVENTORY)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHeld(event: PlayerItemHeldEvent) = mark(event.player, Activity.INVENTORY)
}
