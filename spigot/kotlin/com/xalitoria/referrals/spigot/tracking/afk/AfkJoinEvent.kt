package com.xalitoria.referrals.spigot.tracking.afk

import org.bukkit.entity.Player
import org.bukkit.event.*

class AfkJoinEvent : Event, Cancellable {
    var awayTriggerCommands = mutableListOf<String>()
    var afkType: AfkType
        private set

    private var cancel = false

    private val handlers: HandlerList = HandlerList()
    override fun getHandlers() = handlers

    val player: Player

    constructor(player: Player, commands: MutableList<String>, afkType: AfkType) {
        this.player = player
        this.awayTriggerCommands = commands
        this.afkType = afkType
    }

    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }

    override fun isCancelled(): Boolean = this.cancel
}