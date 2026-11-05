import mc.xalitoria.refer.paper.playtime.Activity
import mc.xalitoria.refer.paper.playtime.Rules
import mc.xalitoria.refer.paper.playtime.Session
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How active time is counted, driven by hand with a clock that only moves when the test says so. */
class PlaytimeRules {
    private val second = 1000L
    private val rules = Rules(afkThreshold = 30 * second, checkInterval = 5 * second, machines = true, window = 120 * second, radius = 6.0, minVariety = 2)
    private val noMachines = rules.copy(machines = false)

    private fun session(rules: Rules, stored: Long = 0) = Session(UUID.randomUUID(), UUID.randomUUID(), stored, rules, start = 0)

    /**
     * Plays [seconds] seconds, a check every 5. [act] says what the player does at second t (the clock has reached it),
     * [at] where they are: x, z, yaw, pitch.
     */
    private fun Session.play(
        seconds: Int,
        from: Int = 0,
        riding: Boolean = false,
        at: (Int) -> List<Double> = { listOf(0.0, 0.0, 0.0, 0.0) },
        act: Session.(Int) -> Unit = {},
    ) {
        for (t in from + 5..from + seconds step 5) {
            act(t)
            val [x, z, yaw, pitch] = at(t)
            sample(t * second, x, z, yaw.toFloat(), pitch.toFloat(), riding)
        }
    }

    private fun Session.did(t: Int, vararg kinds: Activity) = kinds.forEach { mark(it, t * second) }

    /** Walking about and looking around: keys every 10 seconds, the view turning, a new place each time. */
    private fun Session.walk(seconds: Int, from: Int = 0) = play(
        seconds, from,
        at = { t -> listOf(t * 0.5, 0.0, t * 7.0 % 360 - 180, 0.0) },
        act = { t -> if (t % 10 == 0) did(t, Activity.INPUT) },
    )

    @Test
    fun `a player who plays earns all of the time`() {
        val s = session(rules)
        s.walk(120)
        assertEquals(120 * second, s.take())
        assertEquals(0, s.take(), "what was taken is gone")
    }

    @Test
    fun `time is held back until the window is judged`() {
        val s = session(rules)
        s.walk(60)
        assertEquals(0, s.take())
        assertEquals(0, s.total())
        s.walk(60, from = 60)
        assertEquals(120 * second, s.total())
    }

    @Test
    fun `a player who stops doing anything stops earning after the threshold`() {
        val s = session(noMachines)
        s.play(120)
        // The arrival was the last activity: 30 seconds of the 120 count.
        assertEquals(30 * second, s.take())
    }

    @Test
    fun `being AFK for less than the threshold costs nothing`() {
        val s = session(noMachines)
        // Active at 0 and 25, 25 and 50: never 30 seconds without a sign.
        s.play(60, act = { t -> if (t % 25 == 0) did(t, Activity.INPUT) })
        assertEquals(60 * second, s.take())
    }

    @Test
    fun `a break is not counted and play after it is`() {
        val s = session(noMachines)
        s.play(60, act = { t -> if (t == 10) did(t, Activity.CHAT) })
        // Active until second 10, then 30 seconds of grace: 40 of the first 60.
        assertEquals(40 * second, s.take())
        s.play(60, from = 60, act = { t -> if (t == 100) did(t, Activity.CHAT) })
        // Nothing until 100, and the 20 seconds from 100 to the end. Nothing is counted back from the action itself.
        assertEquals(20 * second, s.take())
    }

    @Test
    fun `an action at the end of a silent stretch does not make the stretch active`() {
        val s = session(noMachines)
        s.play(100)
        assertEquals(30 * second, s.take())
        s.mark(Activity.CHAT, 100 * second)
        s.sample(100 * second, 0.0, 0.0, 0f, 0f, false)
        assertEquals(0, s.take())
        s.sample(110 * second, 0.0, 0.0, 0f, 0f, false)
        assertEquals(10 * second, s.take())
    }

    @Test
    fun `a water stream earns nothing`() {
        val s = session(rules)
        // Pushed along a block a second with the view fixed and no key touched.
        s.play(600, at = { t -> listOf(t * 1.0, 0.0, 90.0, 0.0) })
        assertEquals(0, s.take())
    }

    @Test
    fun `a bot that toggles one key in a pen earns nothing`() {
        val s = session(rules)
        s.play(600, act = { t -> if (t % 10 == 0) did(t, Activity.INPUT) })
        assertEquals(0, s.take())
    }

    @Test
    fun `an auto-clicker earns nothing`() {
        val s = session(rules)
        s.play(600, act = { t -> did(t, Activity.INTERACT) })
        assertEquals(0, s.take())
    }

    @Test
    fun `pressing keys and turning in a pen earns nothing`() {
        val s = session(rules)
        s.play(
            600,
            at = { t -> listOf(1.0, 1.0, t * 3.0 % 360, 0.0) },
            act = { t -> if (t % 10 == 0) did(t, Activity.INPUT) },
        )
        assertEquals(0, s.take(), "keys and a turning view are what a machine in a pen does too")
    }

    @Test
    fun `standing still and chatting while looking around earns`() {
        val s = session(rules)
        s.play(
            120,
            at = { t -> listOf(1.0, 1.0, t * 3.0 % 360, 0.0) },
            act = { t -> if (t % 20 == 0) did(t, Activity.CHAT) },
        )
        assertEquals(120 * second, s.take())
    }

    @Test
    fun `building in one spot earns`() {
        val s = session(rules)
        s.play(120, at = { t -> listOf(0.0, 0.0, t * 3.0 % 360, 0.0) }, act = { t -> if (t % 5 == 0) did(t, Activity.BLOCK, Activity.INVENTORY) })
        assertEquals(120 * second, s.take())
    }

    @Test
    fun `a boat that turns by itself is not the player looking around`() {
        val s = session(rules)
        s.play(120, riding = true, at = { t -> listOf(t * 2.0, 0.0, t * 20.0 % 360, 0.0) })
        assertEquals(0, s.take())
    }

    @Test
    fun `the view turning the short way round counts as a small turn`() {
        val s = session(noMachines)
        // 359.9 to 0.1 is a fifth of a degree, not a whole circle: no look, so the AFK threshold ends the time.
        s.play(60, at = { t -> listOf(0.0, 0.0, if (t % 10 == 0) 359.9 else 0.1, 0.0) })
        assertEquals(30 * second, s.take())
    }

    @Test
    fun `a long silence is counted only as far as the threshold`() {
        val s = session(noMachines)
        s.sample(5 * second, 0.0, 0.0, 0f, 0f, false)
        s.mark(Activity.CHAT, 590 * second)
        s.sample(600 * second, 0.0, 0.0, 0f, 0f, false)
        // The 30 seconds after the arrival, and the 10 after the chat.
        assertEquals(30 * second + 10 * second, s.take())
    }

    @Test
    fun `leaving judges the part of a window that is left`() {
        val s = session(rules)
        s.walk(60)
        s.finish(60 * second)
        assertEquals(60 * second, s.take())
        val bot = session(rules)
        bot.play(60, act = { t -> did(t, Activity.INTERACT) })
        bot.finish(60 * second)
        assertEquals(0, bot.take())
    }

    @Test
    fun `time that could not be saved comes back`() {
        val s = session(rules, stored = 10 * second)
        s.walk(120)
        assertEquals(130 * second, s.total())
        val taken = s.take()
        s.giveBack(taken)
        assertEquals(130 * second, s.total())
        s.take()
        s.synced(500 * second)
        assertEquals(500 * second, s.total(), "the database knows what other servers added")
    }

    @Test
    fun `a window judges on its own, not on the one before`() {
        val s = session(rules)
        s.play(120, act = { t -> did(t, Activity.INTERACT) })
        assertEquals(0, s.take())
        s.walk(120, from = 120)
        assertEquals(120 * second, s.take())
    }

    @Test
    fun `rules with machines off accept anything`() {
        assertTrue(noMachines.isHuman(0, confined = true))
        assertFalse(rules.isHuman(0, confined = false))
        assertFalse(rules.isHuman(Activity.INPUT.bit, confined = false), "one kind is a repeated thing")
        assertTrue(rules.isHuman(Activity.INPUT.bit or Activity.LOOK.bit, confined = false))
        assertFalse(rules.isHuman(Activity.INPUT.bit or Activity.LOOK.bit, confined = true))
        assertTrue(rules.isHuman(Activity.LOOK.bit or Activity.CHAT.bit, confined = true))
    }
}
