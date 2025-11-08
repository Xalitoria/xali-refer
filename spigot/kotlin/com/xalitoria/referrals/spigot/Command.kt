package com.xalitoria.referrals.spigot

import com.xalitoria.referrals.spigot.api.ReferralData
import com.xalitoria.referrals.spigot.api.XaliReferAPI
import com.xalitoria.referrals.spigot.config.MainConfig
import com.xalitoria.referrals.spigot.config.setMessagePlaceholders
import dev.idot.text.color.convertColorCodes
import dev.idot.text.color.convertColors
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.*
import org.bukkit.command.Command
import org.bukkit.entity.Player
import java.util.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.plusAssign
import com.xalitoria.referrals.spigot.config.MessageConfig as msg

class Command : CommandExecutor {

    private val cooldowns = hashMapOf<String, Long>()

    @OptIn(ExperimentalAtomicApi::class)
    override fun onCommand(sender: CommandSender, command: Command, alias: String, args: Array<out String>): Boolean {
        val args = args.toMutableList()

        when (val subCommand = args.removeFirstOrNull()) {
        null, "", "info" -> {
            val playerArg = args.removeFirstOrNull()
            if (subCommand.isNullOrBlank() && playerArg.isNullOrBlank()) {
                if (sender !is Player) {
                    msg.SPECIFY_PLAYER.message(sender) { replace("{}", "/$alias info <player>") }
                } else {
                    msg.INFO.message(sender)
                }
                return true
            }
            if (playerArg.isNullOrBlank()) {
                msg.INFO.message(sender)
                return true
            } else {
                val uuid = XaliRefer.luckPerms.userManager.lookupUniqueId(playerArg).get()
                if (uuid == null) {
                    msg.NOT_A_PLAYER.message(sender, sender as Player) { replace("{}", playerArg) }
                    return true
                }
                msg.INFO.message(sender, Bukkit.getOfflinePlayer(uuid))
            }
        }
        "help" -> sender.sendMessage(msg.HELP)
        "admin" -> {
            if (!sender.hasPermission("xalireferral.admin")) {
                msg.NO_PERMISSION_COMMAND.message(sender)
                return true
            }

            when (val adminCommand = args.removeFirstOrNull()) {
            "reload" -> {
                msg.RELOAD_START.message(sender)
                XaliRefer.instance.reload()
                msg.RELOAD_FINISH.message(sender)
            }
            "raffle" -> XaliRefer.runTaskAsync {
                val winner: String?
                mutableListOf<String>().apply {
                    XaliReferAPI.referralData.forEach {
                        val uuid = it.value.referUser.uniqueId
                        val name = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
                        repeat (it.value.totalCount) { add(name) }
                    }
                    winner = randomOrNull()
                    clear()
                }

                if (winner == null) {
                    msg.NO_WINNER.message(sender)
                    return@runTaskAsync
                }
                msg.RAFFLE_WINNER.message(sender) { replace("{}", winner) }
            }
            "give", "take" -> {
                val player = getPlayer(args.removeFirstOrNull(), sender, "/$alias admin $adminCommand <player> [amount]") ?: return true
                val amountArg = args.removeFirstOrNull()
                val amount = if (amountArg == null) 1 else amountArg.toIntOrNull()
                if (amount == null) {
                    msg.NOT_A_NUMBER.message(sender, sender as Player) { replace("{}", amountArg.toString()) }
                    return true
                }
                if (amount == 0) {
                    sender.sendMessage("&cUsage:&f/$alias admin $adminCommand <player> [amount]".convertColorCodes())
                    return true
                }

                fun addData(amount: Int) = XaliReferAPI.referralData
                    .getOrPut(sender.uuid) { ReferralData(player) }
                    .bonusCount.plusAssign(amount)

                fun String.setMsgPh() = this
                    .replace("{refer_name}", player.name.toString())
                    .replace("{refer_uuid}", player.uniqueId.toString())
                    .replace("{amount}", amount.toString())

                when (adminCommand) {
                "give" -> { // admin give <player> [amount]
                    addData(amount)
                    msg.GIVE.message(sender, sender as OfflinePlayer, String::setMsgPh)
                }
                "take" -> { // admin take <player> [amount]
                    addData(-amount)
                    msg.TAKE.message(sender, sender as OfflinePlayer, String::setMsgPh)
                }
                else -> sender.sendMessage("&cUsage: &f/$alias admin $adminCommand [amount]".convertColorCodes())
                }
            }
            "list" -> XaliRefer.runTaskAsync { // admin list <player>
                val now = System.currentTimeMillis()
                val player = getPlayer(args.removeFirstOrNull(), sender, "/$alias admin list <player>") ?: return@runTaskAsync
                if ((cooldowns[sender.name] ?: 0) >= now) return@runTaskAsync
                cooldowns[sender.name] = now + 1000 * 30 // 30 seconds
                val playtimes = XaliReferAPI
                    .getPlaytimesOfPlayer(player.uniqueId)
                    .toList().sortedByDescending { it.second }
                val lines = 10
                val pageMax = if (playtimes.isEmpty()) 1 else ((playtimes.size - 1) / lines) + 1
                val page = args.removeFirstOrNull()?.toIntOrNull()?.coerceIn(1, pageMax) ?: 1
                val message = buildString {
                    append(msg.LIST_HEADER.toString()).append('\n')
                    val start = (page - 1) * lines
                    val end = minOf(page * lines, playtimes.size)

                    for (i in start until end) {
                        val (recipientId, value) = playtimes[i]
                        val recipient = Bukkit.getOfflinePlayer(recipientId)
                        val element = msg.LIST_ELEMENT.toString()
                            .replace("{refer_name}", recipient.name ?: recipientId.toString() )
                            .replace("{refer_ip}", recipient.name?.let {
                                msg.IP.getOrNull()?.replace("{refer_name}", it) ?: (it + MainConfig.ipSearch)
                            } ?: "unknown")
                            .replace("{refer_uuid}", recipientId.toString())
                            .replace("{status}",
                                if (value < MainConfig.playtimeSeconds) msg.LIST_STATUS_IN_PROGRESS.toString()
                                else msg.LIST_STATUS_DONE.toString()
                            ).replace("{refer_playtime}", value.formattedTime())
                        append(element).append('\n')
                    }
                    append(msg.LIST_FOOTER.toString())
                }.replace("{page}", page.toString())
                .replace("{page_max}", pageMax.toString())
                .setMessagePlaceholders(player)
                sender.sendMessage(message.convertColors())
            }
            "save" -> XaliReferAPI.saveAsync(sender)
            "debug" -> if (Debug.toggle()) msg.DEBUG_ON.message(sender) else msg.DEBUG_OFF.message(sender)
            }
        }
        }
        return true
    }

    private fun getPlayer(name: String?, sender: CommandSender, usage: String?): OfflinePlayer? {
        if (name == null) {
            msg.SPECIFY_PLAYER.message(sender) { usage?.let { replace("{}", it) } ?: this }
            return null
        }

        val uuid = XaliRefer.luckPerms.userManager.lookupUniqueId(name).get()
        if (uuid == null) {
            msg.NOT_A_PLAYER.message(sender) { replace("{}", name) }
            return null
        }
        return Bukkit.getOfflinePlayer(uuid)
    }

    private val CommandSender.uuid: UUID get() {
        return when (this) {
            is Player -> return uniqueId
            is OfflinePlayer -> return uniqueId
            is ConsoleCommandSender -> return UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff")
            else -> throw IllegalStateException("Unknown command sender ${this.javaClass.toGenericString()}")
        }
    }
}