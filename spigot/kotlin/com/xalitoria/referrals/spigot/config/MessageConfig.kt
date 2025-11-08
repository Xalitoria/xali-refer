package com.xalitoria.referrals.spigot.config

import dev.idot.text.color.convertColors
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object MessageConfig : Config("messages") {

    data class Message(val path: String) {
        override fun toString(): String = config.getString(path) ?: throw NullPointerException("'$path' is null in messages.yml")

        fun getOrNull(): String? = config.getString(path)

        fun message(sender: CommandSender, recipient: OfflinePlayer) {
            sender.sendMessage(toString().setMessagePlaceholders(recipient))
        }

        fun message(sender: CommandSender) {
            when (sender){
                is Player -> message(sender, sender)
                else -> sender.sendMessage(toString().replace("{prefix}", PREFIX.toString()).convertColors())
            }
        }

        fun message(sender: CommandSender, recipient: OfflinePlayer?, transform: String.() -> String) {
            if (recipient == null) {
                sender.sendMessage(toString().replace("{prefix}", PREFIX.toString()).let(transform).convertColors())
                return
            }
            sender.sendMessage(toString().let(transform).setMessagePlaceholders(recipient))
        }

        fun message(player: CommandSender, transform: String.() -> String) {
            message(player, player as? OfflinePlayer, transform)
        }
    }

    val PREFIX get() = Message("prefix")
    val IP get() = Message("ip")
    val INFO get() = Message("info")
    val JOIN get() = Message("join")
    val GIVE get() = Message("admin.give")
    val TAKE get() = Message("admin.take")
    val LIST_HEADER get() = Message("admin.list.header")
    val LIST_ELEMENT get() = Message("admin.list.element")
    val LIST_FOOTER get() = Message("admin.list.footer")
    val LIST_STATUS_IN_PROGRESS get() = Message("admin.list.status.in-progress")
    val LIST_STATUS_DONE get() = Message("admin.list.status.done")
    val SAVE_REFERRAL_START get() = Message("admin.save.referral.start")
    val SAVE_REFERRAL_FINISH get() = Message("admin.save.referral.finish")
    val SAVE_PLAYTIME_START get() = Message("admin.save.playtime.start")
    val SAVE_PLAYTIME_FINISH get() = Message("admin.save.playtime.finish")
    val SAVE_ALL_START get() = Message("admin.save.all.start")
    val SAVE_ALL_FINISH get() = Message("admin.save.all.finish")
    val RELOAD_START get() = Message("admin.reload.start")
    val RELOAD_FINISH get() = Message("admin.reload.finish")
    val DEBUG_ON get() = Message("admin.debug.enabled")
    val DEBUG_OFF get() = Message("admin.debug.disabled")
    val NO_PERMISSION_COMMAND get() = Message("error.no-command-permission")
    val NOT_A_PLAYER get() = Message("error.not-a-player")
    val SPECIFY_PLAYER get() = Message("error.specify-player")
    val NOT_A_NUMBER get() = Message("error.not-a-number")
    val SPECIFY_NUMBER get() = Message("error.specify-number")
    val NO_WINNER get() = Message("error.no-winner")
    val RAFFLE_WINNER get() = Message("raffle-winner")
    val SELF_REFER get() = Message("error.self-refer")

    val HELP = """
        Referral Commands
        &3/refer &b[subcommand]
          &bip &f- &7get your referral ip
    """.trimIndent().convertColors()

    val ADMIN_HELP = """
        Referral Commands (Admin)
        &3/refer &fadmin &b[subcommand]
          &bip [player] &f- &7get referral ip of player
          &blist <player> &f- &7see players who have used this player's referral ip
    """.trimIndent().convertColors()
}

fun String.setMessagePlaceholders(recipient: OfflinePlayer?): String =
    PlaceholderAPI.setPlaceholders(recipient, replace("{prefix}", MessageConfig.PREFIX.toString())).convertColors()
