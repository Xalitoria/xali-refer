package mc.xalitoria.refer.paper.playtime

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The active time one referred player has earned.
 *
 * Nothing here looks at the server: [mark] says something happened and [sample] says where the player is, so the rules
 * run (and are tested) without one. A player costs a few fields, an event handler costs one short lock, and a check
 * costs a handful of arithmetic operations. There is no per-move work: movement is not read from move events but from
 * the player's key presses ([Activity.INPUT]) and from a look at where they are every [Rules.checkInterval].
 *
 * Every activity keeps the player active for [Rules.afkThreshold] after it, so the active time is the union of those
 * periods. With [Rules.machines] on, time is held back until a window has been judged by [Rules.isHuman], and what a
 * failing window earned is thrown away. With it off, time counts as it passes.
 *
 * Any thread may call anything: activity arrives on the player's thread, but also from chat's own, and the totals are
 * read when they are saved.
 */
class Session(
    val player: UUID,
    val referrer: UUID,
    /** Active milliseconds the database held at the last [synced]; starts as what it held when the player arrived. */
    stored: Long,
    private val rules: Rules,
    start: Long,
) {
    /** Set while a save of this player's time is under way, so that a second one is not started on top of it. */
    val saving = AtomicBoolean()

    /** What passed the judge and has not been handed to [take] yet. */
    private val committed = AtomicLong()

    @Volatile
    private var stored = stored

    // The current stretch of activity: from when it began to when it ends unless there is more, and how much of it has
    // been counted. The arrival is the first activity: someone just connected.
    private var stretchStart = start
    private var activeUntil = start + rules.afkThreshold
    private var creditedTo = start

    // The window being judged: when it began, what it earned, and the kinds of activity and the area it covered.
    private var windowStart = start
    private var windowCredit = 0L
    private var kinds = 0

    // Which way the player faced at the last sample, to tell that they looked around.
    private var hasPosition = false
    private var x = 0.0
    private var z = 0.0
    private var yaw = 0f
    private var pitch = 0f
    private var minX = 0.0
    private var maxX = 0.0
    private var minZ = 0.0
    private var maxZ = 0.0

    /** Records that [activity] happened at [now]. */
    @Synchronized
    fun mark(activity: Activity, now: Long) {
        kinds = kinds or activity.bit
        if (now > activeUntil) {
            // The last stretch ended with nothing after it: count what is left of it, and begin another.
            credit(activeUntil)
            stretchStart = now
            creditedTo = now
        }
        activeUntil = max(activeUntil, now + rules.afkThreshold)
    }

    /**
     * A look at the player at [now]: where they are and which way they face ([riding] when they sit in a vehicle, whose
     * turns are not theirs). Counts the active time up to [now], and judges the window when it ends.
     */
    @Synchronized
    fun sample(now: Long, x: Double, z: Double, yaw: Float, pitch: Float, riding: Boolean) {
        if (hasPosition) {
            if (!riding && (angle(yaw, this.yaw) > TURN || abs(pitch - this.pitch) > TURN)) mark(Activity.LOOK, now)
            minX = min(minX, x); maxX = max(maxX, x)
            minZ = min(minZ, z); maxZ = max(maxZ, z)
        } else {
            hasPosition = true
            resetArea(x, z)
        }
        this.x = x
        this.z = z
        this.yaw = yaw
        this.pitch = pitch

        credit(now)
        if (rules.machines && now - windowStart >= rules.window) judge(now)
    }

    /** Judges what is left of the window when the player leaves or the plugin stops, after one last [sample]. */
    @Synchronized
    fun finish(now: Long) {
        credit(now)
        if (rules.machines && windowCredit > 0) judge(now)
    }

    /** Counts the active time of this stretch that has passed by [now] and was not counted yet. */
    private fun credit(now: Long) {
        val to = min(now, activeUntil)
        val from = max(creditedTo, stretchStart)
        if (to <= from) return
        creditedTo = to
        if (rules.machines) windowCredit += to - from else committed.addAndGet(to - from)
    }

    private fun judge(now: Long) {
        val spread = max(maxX - minX, maxZ - minZ)
        if (windowCredit > 0 && rules.isHuman(kinds, confined = spread <= rules.radius)) committed.addAndGet(windowCredit)
        windowCredit = 0
        kinds = 0
        windowStart = now
        // The next window is measured from where the player stands now, not from the whole path of this one.
        resetArea(x, z)
    }

    private fun resetArea(x: Double, z: Double) {
        minX = x; maxX = x; minZ = z; maxZ = z
    }

    /** Hands over the time that has passed the judge since the last call, in milliseconds, to be saved. */
    fun take(): Long = committed.getAndSet(0)

    /** Hands back time that [take] returned but could not be saved. */
    fun giveBack(millis: Long) {
        committed.addAndGet(millis)
    }

    /** What the database holds after the last save; it also has what other servers or an admin added. */
    fun synced(total: Long) {
        stored = total
    }

    /** Active milliseconds in total: what the database held at the last [synced] and what is waiting to be saved. */
    fun total(): Long = stored + committed.get()

    private fun angle(a: Float, b: Float): Float {
        val difference = abs(a - b) % 360f
        return if (difference > 180f) 360f - difference else difference
    }

    companion object {
        /** Degrees the view has to turn to count as looking around. */
        private const val TURN = 1f
    }
}
