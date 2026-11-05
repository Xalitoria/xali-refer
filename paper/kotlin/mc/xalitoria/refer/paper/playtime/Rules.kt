package mc.xalitoria.refer.paper.playtime

import mc.xalitoria.refer.paper.data.config.MainConfig
import kotlin.time.Duration

/** How time is measured, from `playtime` in the configuration. Times are milliseconds. */
data class Rules(
    val afkThreshold: Long,
    val checkInterval: Long,
    val machines: Boolean,
    val window: Long,
    /** The most blocks the player may range over, in either direction, and still count as standing still. */
    val radius: Double,
    val minVariety: Int,
) {
    /**
     * Whether a window of play, in which the kinds of activity in [kinds] were seen and the player [confined] themselves
     * to a small area or not, looks like someone playing and not like a machine.
     *
     * It needs [minVariety] kinds, since a machine repeats one thing: a held key, a click, a jump. And a player who stays
     * put has to have done something that takes a decision (chat, building, a click in an inventory); turning around and
     * pressing keys is what a machine in a pen does too.
     */
    fun isHuman(kinds: Int, confined: Boolean): Boolean {
        if (!machines) return true
        if (Integer.bitCount(kinds) < minVariety) return false
        return !confined || kinds and Activity.DELIBERATE != 0
    }

    companion object {
        fun of(config: MainConfig.Playtime) = Rules(
            afkThreshold = config.afkThreshold().millis(),
            checkInterval = config.checkInterval().millis(),
            machines = config.machines.enabled,
            window = config.machineWindow().millis(),
            radius = config.machines.radius.toDouble(),
            minVariety = config.machines.minVariety,
        )

        private fun Duration.millis(): Long = inWholeMilliseconds
    }
}
