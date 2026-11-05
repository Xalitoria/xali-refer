package mc.xalitoria.refer.paper.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mc.xalitoria.refer.paper.XaliRefer
import mc.xalitoria.refer.paper.XaliRefer.Companion.debug
import mc.xalitoria.refer.paper.data.db.repository.Claim
import mc.xalitoria.refer.paper.data.db.repository.IpRepository
import mc.xalitoria.refer.paper.data.db.repository.PlayerRepository
import mc.xalitoria.refer.paper.data.db.repository.ReferralRepository
import mc.xalitoria.refer.paper.data.model.Profile
import mc.xalitoria.refer.paper.data.model.Ranked
import mc.xalitoria.refer.paper.playtime.PlaytimeTracker
import mc.xalitoria.refer.paper.playtime.Rules
import mc.xalitoria.refer.paper.playtime.Session
import mc.xalitoria.util.message.fail
import mc.xalitoria.util.message.send
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * What a referral goes through: the player joins with an address ([refer]), plays ([PlaytimeTracker] counts the active
 * time, [save] writes it down) and the referral is complete once they have played long enough ([complete]).
 */
class ReferralService(private val plugin: XaliRefer) {
    val tracker = PlaytimeTracker(plugin, Rules.of(plugin.config.data.playtime))

    private val board = AtomicReference<List<Ranked>>(emptyList())

    private val config get() = plugin.config.data
    private val msg get() = plugin.messageConfig.data

    /** Brings the rules up to date after the configuration changed. Players being watched keep the rules they started with. */
    fun configure() {
        tracker.rules = Rules.of(config.playtime)
        tracker.requirement = config.playtime.requirement().inWholeMilliseconds
    }

    fun start() {
        configure()
        tracker.onReached = { session -> plugin.scope.launch { save(session) } }
        // Both loops read their interval anew each time, so a reload changes them without restarting anything.
        plugin.scope.launch {
            while (isActive) {
                delay(config.playtime.saveInterval())
                tracker.tracked.forEach { session -> plugin.scope.launch { save(session) } }
            }
        }
        plugin.scope.launch {
            while (isActive) {
                refreshBoard()
                delay(config.leaderboard.refresh())
            }
        }
    }

    // --- Joining ---------------------------------------------------------------------------------------------

    /** The name of the player an address refers to: `audizian` for `audizian.xalitoria.com`; null for any other address. */
    fun referrerName(host: String): String? = referrerName(host, config.domain)

    /**
     * A player comes in: their row, the address they joined from ([address], see [IpRepository.normalize]) for the log, the
     * referral they are finishing and referrals that were completed while they were away.
     */
    suspend fun arrive(player: Player, address: String?) {
        val uuid = player.uniqueId
        PlayerRepository.seen(uuid, player.name)
        if (address != null && config.log.enabled) {
            // The log is for admins; failing to write it must not keep the player from their referral.
            try {
                IpRepository.record(uuid, player.name, address)
            } catch (e: Exception) {
                plugin.logger.fail("Failed to log the address of ${player.name}", e)
            }
        }

        val referral = ReferralRepository.get(uuid)
        if (referral != null && !referral.isRedeemed && !referral.isRemoved) {
            tracker.start(player, referral.referrer, referral.playtimeMillis)
            debug("{0} is finishing a referral: {1} ms of {2}", player.name, referral.playtimeMillis, tracker.requirement)
        }

        val completed = ReferralRepository.takeUnannounced(uuid)
        if (completed.isNotEmpty()) player send msg.referral.completedWhileAway(completed.size, completed.joinToString(", "))
    }

    /**
     * [player] connected with [host]. If that is a referral address, records it, unless the player is the referrer, has
     * been referred before or joined too long ago, and tells them. [firstPlayed] is when they first joined the server.
     *
     * The proxy sends the host when the player joins the network, not at every server, so a player using their own
     * address is told so once per visit. Alts are allowed; the address log is there for a person to review them.
     */
    suspend fun refer(player: Player, host: String, firstPlayed: Long) {
        val name = referrerName(host) ?: return
        val uuid = player.uniqueId
        debug("{0} joined with the address of {1}", player.name, name)

        val referrer = PlayerRepository.byName(name)
        if (referrer == null) {
            player send msg.referral.unknownReferrer(name)
            return
        }
        if (referrer.uuid == uuid) {
            player send msg.referral.self
            return
        }
        val window = config.referral.window()
        if (window.isPositive() && firstPlayed > 0 && System.currentTimeMillis() - firstPlayed > window.inWholeMilliseconds) {
            player send msg.referral.tooLate(words(window.inWholeSeconds))
            return
        }
        when (val claim = ReferralRepository.claim(uuid, player.name, referrer.uuid)) {
            Claim.Created -> {
                tracker.start(player, referrer.uuid, 0)
                player send msg.referral.joined(referrer.name, words(config.playtime.requirement().inWholeSeconds))
            }
            // The same address again is what every later join with it is; only another referrer is worth saying.
            is Claim.Already -> if (claim.referrer != referrer.uuid) player send msg.referral.alreadyReferred
        }
    }

    /** The player leaves: their time is judged one last time and saved. Runs on the player's thread. */
    fun leave(player: Player) {
        val session = tracker.stop(player.uniqueId, player) ?: return
        plugin.scope.launch { save(session) }
    }

    // --- Playing ---------------------------------------------------------------------------------------------

    /**
     * Writes down the time [session] has earned, and completes the referral if it is enough. One save at a time per
     * player; a second one asked for meanwhile is skipped, as the next one carries what it would have.
     */
    suspend fun save(session: Session) {
        if (!session.saving.compareAndSet(false, true)) return
        try {
            val millis = session.take()
            val total = try {
                ReferralRepository.addPlaytime(session.player, millis)
            } catch (e: Exception) {
                session.giveBack(millis)
                plugin.logger.fail("Failed to save the playtime of ${session.player}", e)
                return
            }
            if (total == null) {
                // There is no open referral any more: completed from another server, or by an admin.
                tracker.stop(session.player)
                return
            }
            session.synced(total)
            if (total >= tracker.requirement) complete(session.player)
        } finally {
            session.saving.set(false)
        }
    }

    /** Saves every player being watched, for [XaliRefer.onDisable] and `/refer admin save`. Returns how many there were. */
    suspend fun saveAll(): Int {
        val sessions = tracker.tracked.toList()
        sessions.forEach { save(it) }
        return sessions.size
    }

    /**
     * Completes the referral of [referred], if it is open: tells the players, runs the commands of `completion`, and
     * brings the leaderboard up to date. False when it was complete already, by this server or another.
     */
    suspend fun complete(referred: UUID): Boolean {
        val referral = ReferralRepository.get(referred) ?: return false
        if (!ReferralRepository.redeem(referred)) return false
        tracker.stop(referred)

        val names = PlayerRepository.getAll(listOf(referred, referral.referrer))
        val player = names[referred]?.name ?: "?"
        val referrer = names[referral.referrer]?.name ?: "?"
        val standing = ReferralRepository.standing(referral.referrer, referrer)
        debug("{0}'s referral by {1} is complete", player, referrer)

        plugin.server.getPlayer(referred)?.send(msg.referral.completed(referrer))
        val referrerOnline = plugin.server.getPlayer(referral.referrer)
        if (referrerOnline != null) {
            referrerOnline send msg.referral.completedReferrer(player, standing.total.toString())
            ReferralRepository.markAnnounced(referred)
        }

        for (template in config.completion.commands) {
            val command = template
                .replace("{player}", player)
                .replace("{player_uuid}", referred.toString())
                .replace("{referrer}", referrer)
                .replace("{referrer_uuid}", referral.referrer.toString())
                .removePrefix("/")
            plugin.server.globalRegionScheduler.run(plugin) {
                debug("Running '{0}'", command)
                plugin.server.dispatchCommand(plugin.server.consoleSender, command)
            }
        }
        refreshBoard()
        return true
    }

    // --- Review ----------------------------------------------------------------------------------------------

    /**
     * Takes the referral of [referred] out of the count, as [by]: it stops earning time, can't complete and is not counted
     * for the referrer, whether it was complete or not. Commands that ran when it completed are not undone. False when
     * there is no referral or it is out already.
     */
    suspend fun remove(referred: UUID, by: String): Boolean {
        if (!ReferralRepository.remove(referred, by)) return false
        tracker.stop(referred)
        debug("{0} removed the referral of {1}", by, referred)
        refreshBoard()
        return true
    }

    /**
     * Puts the referral of [referred] back in the count. One that had not completed carries on from the time it had, for a
     * player who is online, and completes at once if that is enough. False when it isn't removed.
     */
    suspend fun restore(referred: UUID): Boolean {
        if (!ReferralRepository.restore(referred)) return false
        val referral = ReferralRepository.get(referred)
        if (referral != null && !referral.isRedeemed) {
            if (referral.playtimeMillis >= tracker.requirement) complete(referred)
            else plugin.server.getPlayer(referred)?.let { tracker.start(it, referral.referrer, referral.playtimeMillis) }
        }
        debug("The referral of {0} is restored", referred)
        refreshBoard()
        return true
    }

    // --- The leaderboard -------------------------------------------------------------------------------------

    /** Everyone with a completed referral, as of the last refresh. */
    fun leaderboard(): List<Ranked> = board.get()

    fun positionOf(uuid: UUID): Int? = board.get().firstOrNull { it.standing.uuid == uuid }?.position

    suspend fun refreshBoard() {
        try {
            board.set(ReferralRepository.leaderboard())
        } catch (e: Exception) {
            plugin.logger.fail("Failed to refresh the leaderboard", e)
        }
    }

    /** [seconds] in the words of the language. */
    fun words(seconds: Long): String = msg.words.duration(seconds)

    /** The address [player] joined from, as the log has it; null when there isn't one to know. */
    fun addressOf(player: Player): String? = IpRepository.normalize(player.address?.address)

    /** [Profile] of a player who is online, without asking the database. */
    fun profileOf(player: Player) = Profile(player.uniqueId, player.name)

    companion object {
        private val NAME = Regex("[a-z0-9_]{1,24}")

        /**
         * The player named by the first part of [host] when it is a subdomain of [domain], in any case and with or
         * without a port; null otherwise. `a.b.xalitoria.com` names no one: a name has no dot.
         */
        fun referrerName(host: String, domain: String): String? {
            val clean = host.trim().lowercase().substringBeforeLast(':').trimEnd('.')
            val suffix = "." + domain.trim().lowercase().trim('.')
            if (!clean.endsWith(suffix)) return null
            return clean.removeSuffix(suffix).takeIf { NAME.matches(it) }
        }

        /** A winner among [entries], each with as many tickets as their total; null when there are none. */
        fun draw(entries: List<Ranked>, random: Random = Random): Profile? {
            val total = entries.sumOf { it.standing.total }
            if (total <= 0) return null
            var ticket = random.nextInt(total)
            for (entry in entries) {
                ticket -= entry.standing.total
                if (ticket < 0) return Profile(entry.standing.uuid, entry.standing.name)
            }
            return null
        }
    }
}
