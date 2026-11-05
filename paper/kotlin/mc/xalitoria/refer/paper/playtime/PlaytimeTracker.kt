package mc.xalitoria.refer.paper.playtime

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import mc.xalitoria.refer.paper.XaliRefer
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Watches the players who have a referral to finish: a [Session] each, and a task on the player's own scheduler that
 * looks at them every `playtime.check-interval`. Everyone else costs nothing: no task, and an event handler that finds no
 * session returns at once. The task runs where the player is (a region thread on Folia), which is the only place their
 * position may be read.
 */
class PlaytimeTracker(private val plugin: XaliRefer, @Volatile var rules: Rules) {
    private val sessions = ConcurrentHashMap<UUID, Session>()
    private val tasks = ConcurrentHashMap<UUID, ScheduledTask>()

    /** Called from a player's check when what they earned adds up to the requirement. */
    var onReached: (Session) -> Unit = {}

    @Volatile
    var requirement: Long = 0

    val isEmpty: Boolean get() = sessions.isEmpty()
    val tracked: Collection<Session> get() = sessions.values

    fun session(uuid: UUID): Session? = sessions[uuid]

    /** Starts watching [player]; [stored] is the active milliseconds their referral already has. Does nothing if they are watched. */
    fun start(player: Player, referrer: UUID, stored: Long) {
        val uuid = player.uniqueId
        val session = Session(uuid, referrer, stored, rules, now())
        if (sessions.putIfAbsent(uuid, session) != null) return
        val ticks = max(1L, rules.checkInterval / MILLIS_PER_TICK)
        val task = player.scheduler.runAtFixedRate(plugin, { check(player, session) }, { drop(uuid) }, ticks, ticks)
        if (task != null) tasks[uuid] = task else sessions.remove(uuid)
    }

    private fun check(player: Player, session: Session) {
        if (!player.isOnline) return
        session.sample(now(), player.x, player.z, player.yaw, player.pitch, player.isInsideVehicle)
        if (session.total() >= requirement) onReached(session)
    }

    /**
     * Stops watching [uuid] and returns their session, or null if they weren't watched. [player] is given when they are
     * still there on their own thread, for one last look that credits the time since the last check.
     */
    fun stop(uuid: UUID, player: Player? = null): Session? {
        val session = sessions.remove(uuid) ?: return null
        tasks.remove(uuid)?.cancel()
        if (player != null) session.sample(now(), player.x, player.z, player.yaw, player.pitch, player.isInsideVehicle)
        session.finish(now())
        return session
    }

    private fun drop(uuid: UUID) {
        sessions.remove(uuid)
        tasks.remove(uuid)
    }

    /** Records that the player [uuid] did [activity]; nothing happens for a player who isn't watched. */
    fun mark(uuid: UUID, activity: Activity) {
        val session = sessions[uuid] ?: return
        session.mark(activity, now())
    }

    /** Stops every watch, for when the plugin stops or reloads; the sessions are returned so the time can be saved. */
    fun stopAll(): List<Session> {
        val all = sessions.values.toList()
        sessions.clear()
        tasks.values.forEach { it.cancel() }
        tasks.clear()
        all.forEach { it.finish(now()) }
        return all
    }

    companion object {
        private const val MILLIS_PER_TICK = 50L

        /** A clock that only goes forward, in milliseconds; all of a session's times are on it. */
        fun now(): Long = System.nanoTime() / 1_000_000
    }
}
