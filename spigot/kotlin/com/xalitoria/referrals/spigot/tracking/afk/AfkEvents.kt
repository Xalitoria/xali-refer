package com.xalitoria.referrals.spigot.tracking.afk

import com.xalitoria.referrals.spigot.Debug
import com.xalitoria.referrals.spigot.PlayerID
import com.xalitoria.referrals.spigot.api.XaliReferAPI
import com.xalitoria.referrals.spigot.api.XaliReferAPI.playtimeData
import com.xalitoria.referrals.spigot.config.MainConfig
import com.xalitoria.referrals.spigot.formattedTime
import com.xalitoria.referrals.spigot.tracking.ReferProxyListener
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.minusAssign

class AfkEvents : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val now = System.currentTimeMillis()

        val uuid = event.player.uniqueId
        _joinTimes[uuid] = now
        _lastActivity[uuid] = now
        _lastLocations[uuid] = event.player.location
        _isAFK[uuid] = false

        ReferProxyListener.Companion.sendRequest(event.player)
    }

    @OptIn(ExperimentalAtomicApi::class)
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val now = System.currentTimeMillis()
        val uuid = event.player.uniqueId

        if (!_isAFK.getOrDefault(uuid, false)) {
            val activeSinceLastCheck = (now - _lastActivity.getOrDefault(uuid, now)) / 1000
            if (activeSinceLastCheck < MainConfig.afkThresholdSeconds * 1000) {
                playtimeData[uuid]?.playtimeMillis?.minusAssign(activeSinceLastCheck)
            }
        }

        Debug("${event.player.name} was on for ${playtimeData[uuid]?.playtimeMillis?.load()?.formattedTime()}")

        _joinTimes.remove(uuid)
        _lastActivity.remove(uuid)
        _lastLocations.remove(uuid)
        _isAFK.remove(uuid)
        XaliReferAPI.save(event.player)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val uuid = event.player.uniqueId
        val from = _lastLocations[uuid] ?: return
        val to = event.to ?: return

        // Only count as activity if moved significantly (e.g., >0.5 blocks, including head rotation)
        if (from.distanceSquared(to) > 0.25 || from.direction != to.direction) {
            _lastActivity[uuid] = System.currentTimeMillis()
            _lastLocations[uuid] = to
        }
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        _lastActivity[event.player.uniqueId] = System.currentTimeMillis()
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        _lastActivity[event.player.uniqueId] = System.currentTimeMillis()
    }

    companion object {
        private val _joinTimes: MutableMap<PlayerID, Long> = HashMap() // Join timestamp
        private val _lastActivity: MutableMap<PlayerID, Long> = HashMap() // Last activity timestamp
        private val _lastLocations: MutableMap<PlayerID, Location> = HashMap() // For movement detection
        private val _isAFK: MutableMap<PlayerID, Boolean> = HashMap() // AFK status
    }
}