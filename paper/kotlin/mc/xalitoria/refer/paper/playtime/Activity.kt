package mc.xalitoria.refer.paper.playtime

/** The kinds of thing a player does that show someone is playing. Each is a bit, so a window of play is one number. */
enum class Activity(val bit: Int, val deliberate: Boolean) {
    /** A movement key, jump, sneak or sprint was pressed or released (the client's own input, which a water stream can't fake). */
    INPUT(1, false),

    /** The view turned. */
    LOOK(2, false),
    CHAT(4, true),
    COMMAND(8, true),

    /** A block was broken or placed. */
    BLOCK(16, true),

    /** Something was used, clicked or hit. */
    INTERACT(32, true),

    /** An inventory slot was clicked, an item dropped, or the hotbar changed. */
    INVENTORY(64, true);

    companion object {
        /** All the kinds that need a decision, not just a key held. */
        val DELIBERATE: Int = entries.filter { it.deliberate }.fold(0) { bits, kind -> bits or kind.bit }
    }
}
