package mc.xalitoria.refer.paper.external

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import mc.xalitoria.refer.paper.XaliRefer
import org.bukkit.OfflinePlayer

/**
 * `%xalirefer_...%`. Everything is answered from memory (the leaderboard as of its last refresh, and the time the server
 * has counted for a player who is online), never from the database, since placeholders are asked for on a region thread.
 *
 * - `address`: the address to share, `<name>.<domain>`.
 * - `count`, `count_referrals`, `count_bonus`: the player's total, the completed referrals in it and the bonus in it.
 * - `position`: their place on the leaderboard; empty when they are not on it.
 * - `playtime`, `playtime_formatted`: the active seconds their own referral has, and the same in words.
 * - `required`, `required_formatted`: the active seconds a referral needs. `progress`: percent of it done (100 if there is nothing to finish).
 * - `top_<n>_name`, `top_<n>_count`, `top_<n>_position`: place n (from 1) of the leaderboard.
 */
class ReferExpansion(private val plugin: XaliRefer) : PlaceholderExpansion() {
    override fun getIdentifier() = "xalirefer"
    override fun getAuthor() = "audizian"
    override fun getVersion() = plugin.pluginMeta.version
    override fun persist() = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val service = plugin.service
        val key = params.lowercase()
        if (key.startsWith("top_")) return top(key.removePrefix("top_"))
        if (key == "required" || key == "required_formatted") {
            val seconds = plugin.config.data.playtime.requirement().inWholeSeconds
            return if (key == "required") seconds.toString() else service.words(seconds)
        }

        val uuid = player?.uniqueId ?: return ""
        val standing = service.leaderboard().firstOrNull { it.standing.uuid == uuid }?.standing
        return when (key) {
            "address" -> player.name?.let { "$it.${plugin.config.data.domain}" }.orEmpty()
            "count" -> (standing?.total ?: 0).toString()
            "count_referrals" -> (standing?.referrals ?: 0).toString()
            "count_bonus" -> (standing?.bonus ?: 0).toString()
            "position" -> service.positionOf(uuid)?.toString().orEmpty()
            "playtime" -> seconds(uuid).toString()
            "playtime_formatted" -> service.words(seconds(uuid))
            "progress" -> {
                val session = service.tracker.session(uuid) ?: return "100"
                (session.total() * 100 / service.tracker.requirement.coerceAtLeast(1)).coerceIn(0, 100).toString()
            }
            else -> null
        }
    }

    /** Active seconds counted for [uuid]; 0 for a player who has nothing to finish, or isn't on this server. */
    private fun seconds(uuid: java.util.UUID): Long = (plugin.service.tracker.session(uuid)?.total() ?: 0) / 1000

    private fun top(rest: String): String? {
        val place = rest.substringBefore('_').toIntOrNull() ?: return null
        val entry = plugin.service.leaderboard().getOrNull(place - 1)
        return when (rest.substringAfter('_', "")) {
            "name" -> entry?.standing?.name.orEmpty()
            "count" -> entry?.standing?.total?.toString().orEmpty()
            "position" -> entry?.position?.toString().orEmpty()
            else -> null
        }
    }
}
