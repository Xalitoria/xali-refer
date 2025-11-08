package com.xalitoria.referrals.spigot.api

import com.xalitoria.referrals.spigot.*
import com.xalitoria.referrals.spigot.XaliRefer.Companion.instance
import com.xalitoria.referrals.spigot.XaliRefer.Companion.luckPerms
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import net.luckperms.api.node.types.MetaNode
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getConsoleSender
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.atomic.*
import java.util.logging.*
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.*
import com.xalitoria.referrals.spigot.config.MessageConfig as msg

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalAtomicApi::class)
object XaliReferAPI {

    data class LeaderboardEntry(
        val playerId: PlayerID,
        val username: String,
        val count: Int
    ) {
        var position: Int = 0
            internal set
    }

    internal val playtimeData = hashMapOf<PlayerID, PlaytimeData>()
    fun getPlaytimes() = playtimeData.toMap()

    internal val referralData = hashMapOf<PlayerID, ReferralData>()
    fun getReferrals() = referralData.toMap()

    private val leaderboardRef = AtomicReference<List<LeaderboardEntry>>(emptyList())
    fun getLeaderboard(): List<LeaderboardEntry> = leaderboardRef.get()

    private const val REFRESH_INTERVAL_TICKS = 5L * 60 * 20

    private val scopeTopReferrals = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var lastUpdateTimeMillis: Long = 0L
    private var refreshTask: BukkitTask? = null
    private val refreshMutex = Mutex()

    fun getTopReferrers(limit: Int = 0): List<LeaderboardEntry> {
        val list = leaderboardRef.get()
        return if (limit <= 0) list else list.take(limit)
    }

    fun getTopReferrerPosition(playerId: PlayerID): Int? =
        leaderboardRef.get().find { it.playerId == playerId }?.position

    fun saveReferrals() {
        msg.SAVE_REFERRAL_START.message(getConsoleSender())
        referralData.values.forEach(ReferralData::save)
        msg.SAVE_REFERRAL_FINISH.message(getConsoleSender())
    }

    fun savePlayTimes() {
        msg.SAVE_PLAYTIME_START.message(getConsoleSender())
        playtimeData.values.forEach(PlaytimeData::save)
        msg.SAVE_PLAYTIME_FINISH.message(getConsoleSender())
    }

    fun saveAsync(sender: CommandSender? = null) = XaliRefer.runTaskAsync {
        save(sender)
    }

    fun save(sender: CommandSender? = null) {
        sender?.let { msg.SAVE_ALL_START.message(it) }
        saveReferrals()
        savePlayTimes()
        luckPerms.runUpdateTask()
        sender?.let { msg.SAVE_ALL_FINISH.message(it) }
    }

    fun save(player: Player) {
        val playtime = playtimeData[player.uniqueId] ?: return
        playtime.save()
        luckPerms.runUpdateTask()
        referralData[playtime.referUser.uniqueId]?.save()
    }

    fun stopRefreshTopReferrals() {
        refreshTask?.cancel()
        scopeTopReferrals.cancel() // call this on plugin disable
    }

    private fun scheduleRefreshTopReferrals() {
        // Only cancel the Bukkit task when rescheduling; keep the coroutine scope alive.
        refreshTask?.cancel()
        refreshTask = XaliRefer.runTaskLater(REFRESH_INTERVAL_TICKS) { refreshTopReferrals() }
    }

    fun refreshTopReferrals() = scopeTopReferrals.launch {
        // Prevent overlapping refreshes; if one is running, skip.
        val locked = refreshMutex.tryLock()
        if (!locked) return@launch
        try {
            val data = luckPerms.userManager
                .searchAll<MetaNode> { it.key == "refer-count" }.await()
                .entries.mapNotNull { (playerId, nodes) ->
                    val maxCount = nodes.maxOfOrNull { it.metaValue.toIntOrNull() ?: 0 } ?: 0
                    if (maxCount <= 0) return@mapNotNull null
                    val username = Bukkit.getOfflinePlayer(playerId).name ?: "unknown"
                    LeaderboardEntry(playerId, username, maxCount)
                }
                .sortedByDescending { it.count }

            // Compute positions with ties
            val newLeaderboard = ArrayList<LeaderboardEntry>(data.size)
            var currentPosition = 1
            var previousCount = Int.MIN_VALUE
            data.forEachIndexed { index, entry ->
                if (entry.count < previousCount) {
                    currentPosition = index + 1
                }
                previousCount = entry.count
                entry.position = currentPosition
                newLeaderboard.add(entry)
            }

            leaderboardRef.set(newLeaderboard)
            lastUpdateTimeMillis = System.currentTimeMillis()
            instance.logger.info("Refreshed leaderboard with ${newLeaderboard.size} entries")
        } catch (_: CancellationException) {
            // Coroutine cancelled; ignore
        } catch (ex: Exception) {
            instance.logger.log(Level.WARNING, "Failed to refresh leaderboard data", ex)
        } finally {
            if (refreshMutex.isLocked) {
                refreshMutex.unlock()
            }
            scheduleRefreshTopReferrals()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun getPlaytimesForReferrer(uuid: ReferID): Map<UUID, Long> {
        val metaKey = "refer-time:" + uuid.toKotlinUuid().toHexString()
        return luckPerms.userManager
            .searchAll<MetaNode> { it.key == metaKey }.get().entries
            .mapNotNull { (playerId, nodes) ->
                nodes.firstOrNull { it.metaKey == metaKey }
                    ?.metaValue?.toLongOrNull()?.let { playerId to it }
            }.toMap()
    }

    @OptIn(ExperimentalUuidApi::class)
    fun getPlaytimesOfPlayer(uuid: PlayerID): Map<UUID, Long> {
        val user = luckPerms.userManager.getUser(uuid) ?: return emptyMap()
        return user.cachedData.metaData.meta
            .filterKeys { it.startsWith("refer-time:") }
            .mapNotNull { (key, values) ->
                val referrerId = runCatching {
                    Uuid.parse(key.removePrefix("refer-time:")).toJavaUuid()
                }.getOrNull()
                val maxPlaytime = values.mapNotNull { it.toLongOrNull() }.maxOrNull()
                if (referrerId == null || maxPlaytime == null) null else referrerId to maxPlaytime
            }.toMap()
    }
}