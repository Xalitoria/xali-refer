package com.xalitoria.referrals.spigot.tracking

import com.google.common.io.ByteStreams
import com.google.common.io.ByteStreams.newDataInput
import com.xalitoria.referrals.spigot.Debug
import com.xalitoria.referrals.spigot.XaliRefer
import com.xalitoria.referrals.spigot.XaliRefer.Companion.PLUGIN_CHANNEL
import com.xalitoria.referrals.spigot.XaliRefer.Companion.instance
import com.xalitoria.referrals.spigot.XaliRefer.Companion.logger
import com.xalitoria.referrals.spigot.XaliRefer.Companion.luckPerms
import com.xalitoria.referrals.spigot.api.PlaytimeData
import com.xalitoria.referrals.spigot.api.ReferralData
import com.xalitoria.referrals.spigot.api.XaliReferAPI.playtimeData
import com.xalitoria.referrals.spigot.api.XaliReferAPI.referralData
import com.xalitoria.referrals.spigot.config.MainConfig
import com.xalitoria.referrals.spigot.config.MessageConfig
import dev.idot.text.color.convertColorCodes
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.logging.*

@Suppress("UnstableApiUsage")
class ReferProxyListener : PluginMessageListener {
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != PLUGIN_CHANNEL) return
        try {
            val input = newDataInput(message)
            when (val subChannel = input.readUTF()) {
            "ReferralResponse" -> {
                val returnedIp = input.readUTF()
                Debug("${player.name} has joined with $returnedIp")

                val referName = returnedIp.trim().removeSuffix("." + MainConfig.ipSearch)
                Debug("${player.name} is attempting to refer '$referName'")
                if (referName.isBlank() || referName.contains('.')) return

                val referId = luckPerms.userManager.lookupUniqueId(referName).get()
                if (referId == null) {
                    player.sendMessage("&c$referName has not joined this server!".convertColorCodes())
                    Debug("$referName has not joined this server!")
                    return
                }

                val playerId = player.uniqueId
                if (playerId == referId) {
                    MessageConfig.SELF_REFER.message(player)
                    return
                }
                //val playerUser = luckPerms.userManager.loadUser(playerId).get()
                val referUser = luckPerms.userManager.loadUser(referId).get()
                val realName = Bukkit.getOfflinePlayer(referId).name ?: "unknown"

                MessageConfig.JOIN.message(player) { replace("{referrer}", realName) }
                Debug("${player.name} joined with referral $realName")

                playtimeData[playerId] = PlaytimeData(playerId, referUser)
                referralData[playerId] = ReferralData(referUser)
            }

            else -> logger.warning("Received unknown sub-channel: $subChannel")
            }
        } catch (ex: Exception) {
            logger.log(Level.WARNING, "Error processing plugin message.", ex)
        }
    }

    companion object {
        fun sendRequest(player: Player) = XaliRefer.runTaskLater(20L) {
            try {
                Debug("Creating plugin message for ${player.name} on channel: $PLUGIN_CHANNEL")
                val output = ByteStreams.newDataOutput()
                output.writeUTF("ReferralRequest")
                output.writeUTF(player.uniqueId.toString())
                player.sendPluginMessage(instance, PLUGIN_CHANNEL, output.toByteArray())
                player.sendPluginMessage(instance, "BungeeCord", output.toByteArray())
            } catch (ex: Exception) {
                logger.log(Level.WARNING, "Error requesting referral data", ex)
            }
        }
    }
}