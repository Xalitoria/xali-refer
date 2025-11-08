package com.xalitoria.referrals.spigot

import com.xalitoria.referrals.spigot.config.MainConfig
import com.xalitoria.referrals.spigot.config.MessageConfig as msg
import com.xalitoria.referrals.spigot.api.XaliReferAPI.getTopReferrerPosition
import com.xalitoria.referrals.spigot.api.XaliReferAPI.getTopReferrers
import com.xalitoria.referrals.spigot.api.XaliReferAPI.playtimeData
import com.xalitoria.referrals.spigot.api.XaliReferAPI.referralData
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class Placeholders(val plugin: XaliRefer) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "xrefer"
    override fun getAuthor(): String = plugin.description.authors.first()
    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    @OptIn(ExperimentalAtomicApi::class)
    override fun onRequest(player: OfflinePlayer, params: String): String? {
        val args = params.split("_").toMutableList()
        return when (args.removeFirstOrNull()) {
        "id" -> referralData[player.uniqueId]?.referUser?.uniqueId.toString()
        "name" -> referralData[player.uniqueId]?.referUser?.username
        "ip" -> referralData[player.uniqueId]?.referUser?.username?.let {
            msg.IP.getOrNull()?.replace("{refer_name}", it) ?: ("$it." + MainConfig.ipSearch)
        } ?: "unknown"
        "count" -> when (args.removeFirstOrNull()) {
            "total", null -> referralData[player.uniqueId]?.totalCount?.toString()
            "refer" -> referralData[player.uniqueId]?.referCount?.toString()
            "bonus" -> referralData[player.uniqueId]?.bonusCount?.toString()
            else -> params
        } ?: "0"
        "position" -> getTopReferrerPosition(player.uniqueId).toString()
        "playtime" -> {
            val seconds = playtimeData[player.uniqueId]?.playtimeMillis?.load()?.div(1000) ?: 0
            if (args.removeFirstOrNull() == "formatted") seconds.formattedTime() else seconds.toString()
        }
        "top" -> {
            fun entry() = args.removeFirstOrNull()?.toIntOrNull()?.let { getTopReferrers()[it] }
            when (args.removeFirstOrNull() ?: return params) {
            "player" -> entry()?.username
            "position" -> entry()?.position.toString()
            "count" -> entry()?.count.toString()
            else -> params
            }
        }
        else -> params
        }
    }
}