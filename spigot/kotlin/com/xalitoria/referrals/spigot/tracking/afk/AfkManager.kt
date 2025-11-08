package com.xalitoria.referrals.spigot.tracking.afk

import com.xalitoria.referrals.spigot.XaliRefer
import com.xalitoria.referrals.spigot.api.XaliReferAPI
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import kotlin.collections.iterator
import kotlin.concurrent.atomics.ExperimentalAtomicApi

object AfkManager {
    private const val AutoKickFromPlayerCount = 0
    private const val RepeatingAutoKickInterval = 5000L
    private const val PreventJumping = true
    private const val TitleMessage = true
    private const val SubTitleMessage = true
    private const val PreventDamage = true
    private const val PreventMobDamage = true
    private const val StopPlayTime = false
    private const val SmartInteractCheck = true
    private const val AntiAfkMachines = true
    private const val DisableOnInventoryClick = true
    private const val DisableOnitemDrop = true
    private const val RepeatKickCommand = false
    private const val PreventMobSpawning = false
    private const val PreventMobSpawningNatural = false
    private const val PreventMobSpawningSpawners = true
    private const val PreventMobVillageDefence = true
    private const val PreventPushing = true
    private const val PreventHook = true

    private val lastAction = HashMap<Player, Long>()
    private val afkPlayersMap = HashMap<UUID, AfkInfo>()
    private val afkKickTimers: NavigableMap<Int, Long> = TreeMap<Int, Long>()
    private val awayTriggerCommands = mutableListOf<String>()
    private val manualAwayTriggerCommands = mutableListOf<String>()
    private val manualLeaveCommands = mutableListOf<String>()
    private val autoLeaveCommands = mutableListOf<String>()
    private val kickTriggerCommands = mutableListOf<String>()
    private val disabledWorlds = mutableListOf<String>()
    private var scheduler: BukkitTask? = null
    private var afkScheduler: BukkitTask? = null

    private var awayTriggerTime: Long = 3000L
    private var defaultKickTime: Long = 5000L
    private var interval = 1
    private var disableOnPublicChat = true
    private var disableOnPrivateChat = true
    private var disableOnInteract = true
    private var disableOnCommand = true
    private var disableOnMove = true
    private var disableOnLookAround = true
    private var disableFishing = true
    private var disableItemPickup = false
    private var disableExpPickup = false
    private var disableShulkerTargeting = false

    var afkSubTitle = mutableListOf<String>()
    var afkList = mutableListOf<Player>()

    fun getClosestKickTime(): Long {
        if (this.defaultKickTime < 0L) return this.defaultKickTime
        val i: Int = XaliRefer.instance.server.onlinePlayers.size
        val integer = this.afkKickTimers.floorKey(i)
        return if (integer != null) (this.afkKickTimers[integer] as Long) else this.defaultKickTime
    }

    fun Player.isAfk(): Boolean {
        if (!afkPlayersMap.containsKey(uniqueId)) return false
        return getAfkInfo().afkFrom > 0L
    }

    fun Player.getAfkInfo(): AfkInfo =
        afkPlayersMap[uniqueId] ?: createAfkInfo()

    fun Player.createAfkInfo(): AfkInfo =
        afkPlayersMap.computeIfAbsent(uniqueId) { paramUUID: UUID? -> AfkInfo() }

    fun Player.removeAfkInfo() {
        afkPlayersMap.remove(uniqueId)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun Player.setAfk(afkState: Boolean, afkType: AfkType = AfkType.MANUAL) {
        val now = System.currentTimeMillis()
        if (getAfkInfo().afkFrom > now) return
        if (afkState) {
            if (isAfk()) return
            if (afkType == AfkType.AUTO && player?.hasPermission("") != true) return
            XaliRefer.runTask {
                var commands = if (afkType == AfkType.AUTO) awayTriggerCommands else manualAwayTriggerCommands
                val joinEvent = AfkJoinEvent(this, commands, afkType)
                XaliRefer.instance.server.pluginManager.callEvent(joinEvent)
                if (joinEvent.isCancelled()) return@runTask

                commands = if (afkState) joinEvent.awayTriggerCommands else mutableListOf()
                if (isVanished() && !VanishManager.`is`(uniqueId, VanishAction.afkcommands)) commands.clear()
                setUserToAfk(commands)
                if (SleepStats.SleepingExcludeAfk) try {
                    isSleepingIgnored = true
                } catch (throwable: Throwable) {
                    throwable.printStackTrace()
                }
                val afkInfo = getAfkInfo()
                afkInfo.afkFrom = now
                afkInfo.playtimeAtAfkStart = XaliReferAPI.playtimeData[uniqueId]!!.playtimeMillis.load()
                afkInfo.type = afkType
                if (isOnline) afkInfo.afkAt = CMIVectorInt3D(location)
                showTitle(true)
            }
        } else if (isAfk()) {
            XaliRefer.runTask {
                if (!isOnline) return@runTask
                val commands = if (getAfkInfo().type == AfkType.AUTO) autoLeaveCommands else manualLeaveCommands
                val quitEvent = CMIAfkLeaveEvent(
                    this,
                    commands,
                    System.currentTimeMillis() - paramLong,
                    getAfkInfo().reason
                )
                XaliRefer.instance.server.pluginManager.callEvent(quitEvent)
                turnOffAfk(
                    afkType,
                    if (afkState) quitEvent.getLeaveTriggerCommands() else emptyList()
                )
            }
        }
    }

    fun stop() {
        scheduler?.let {
            it.cancel()
            scheduler = null
        }
        afkScheduler?.let {
            it.cancel()
            afkScheduler = null
        }
        afkKickTimers.clear()
    }

    private fun loadAfk() {
        afkSubTitle = TODO() // CMILC.getIML("afk", "afkSubTitle", arrayOfNulls<Any>(0))
    }

    private fun tasker() {
        if (scheduler != null) return
        val now = System.currentTimeMillis()
        scheduler = XaliRefer.runTaskRepeat(1L, interval * 20L) {
            for ((player, value) in lastAction) {
            if (!player.isOnline) {
                removeUser(player)
                player.setAfk(false, AfkType.AUTO)
                continue
            }

            val afkInfo = player.getAfkInfo()
            val afkFrom = afkInfo.afkFrom
            val l1: Long = now - if (!player.isAfk() || afkFrom == 0L) value else afkFrom

            if (this.awayTriggerTime > 0L) {
                if (!player.isAfk() && l1 < this.awayTriggerTime) {
                    if (player.getAfkInfo().type == AfkType.AUTO)
                        player.setAfk(false, AfkType.AUTO)
                    continue
                }
                if (!player.isAfk() && l1 > this.awayTriggerTime)
                    player.setAfk(true, AfkType.AUTO)
            }
            if (!player.isAfk())
                continue
            if (AutoKickFromPlayerCount > 0 && XaliRefer.instance.server.onlinePlayers.size <= AutoKickFromPlayerCount) {
                showTitle(player, false)
                continue
            }
            val l2 = afkInfo.kickOutInSec * 1000L;
            if (l2 > 0L && l1 > l2 + afkInfo.kicksPerformed * this.RepeatingAutoKickInterval && player.isAfk()) {
                if ((afkInfo.kicksPerformed > 0 && RepeatKickCommand) || afkInfo.kicksPerformed == 0) {
                    val kickEvent = CMIAfkKickEvent(player, kickTriggerCommands)
                    XaliRefer.instance.server.pluginManager.callEvent(kickEvent)
                    if (!kickEvent.isCancelled()) {
                        val snd = Snd().setSender(player).setTarget(player)
                        val list = kickEvent.getKickTriggerCommands()
                        for (cmd in list) {
                            var str = this.plugin.getLM().updateSnd(snd, cmd)
                            str = str.replace("[time]", CMITimeManager.to24hourShort(Long.valueOf(l2)))
                            list.set(b, str)
                        }
                        this.plugin.getSpecializedCommandManager().processCmds(list, if (player.isOnline) player else null)
                    }
                }
                afkInfo.kicksPerformed++
            }
            showTitle(player, false);
        }
        }
    }

}