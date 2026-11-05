package mc.xalitoria.refer.paper.data.db.repository

import mc.xalitoria.refer.paper.data.db.DatabaseManager.dbTransaction
import mc.xalitoria.refer.paper.data.db.repository.PlayerRepository.upsertPlayer
import mc.xalitoria.refer.paper.data.db.tables.BonusTable
import mc.xalitoria.refer.paper.data.db.tables.PlayerTable
import mc.xalitoria.refer.paper.data.db.tables.ReferralTable
import mc.xalitoria.refer.paper.data.model.Ranked
import mc.xalitoria.refer.paper.data.model.Referral
import mc.xalitoria.refer.paper.data.model.ReferralEntry
import mc.xalitoria.refer.paper.data.model.Standing
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/** The outcome of [ReferralRepository.claim]. */
sealed interface Claim {
    /** The referral is recorded. */
    data object Created : Claim

    /** The player was referred before, by [referrer]. */
    data class Already(val referrer: UUID) : Claim
}

object ReferralRepository {
    private fun ResultRow.toReferral() = Referral(
        referred = this[ReferralTable.referred].toJavaUuid(),
        referrer = this[ReferralTable.referrer].toJavaUuid(),
        created = this[ReferralTable.created],
        playtimeMillis = this[ReferralTable.playtime],
        redeemed = this[ReferralTable.redeemed],
        removed = this[ReferralTable.removed],
        removedBy = this[ReferralTable.removedBy],
        announced = this[ReferralTable.announced],
    )

    /**
     * Records that [referred] (called [referredName]) joined with [referrer]'s address. A player has one referral, ever:
     * the first claim wins, also when two servers claim at once, as the row's key is the referred player.
     */
    suspend fun claim(referred: UUID, referredName: String, referrer: UUID, now: Long = System.currentTimeMillis()): Claim =
        dbTransaction {
            upsertPlayer(referred, referredName)
            val inserted = ReferralTable.insertIgnore {
                it[ReferralTable.referred] = referred.toKotlinUuid()
                it[ReferralTable.referrer] = referrer.toKotlinUuid()
                it[created] = now
            }
            if (inserted.insertedCount > 0) Claim.Created
            else Claim.Already(
                ReferralTable.selectAll().where { ReferralTable.referred eq referred.toKotlinUuid() }.single()[ReferralTable.referrer].toJavaUuid()
            )
        }

    suspend fun get(referred: UUID): Referral? = dbTransaction {
        ReferralTable.selectAll().where { ReferralTable.referred eq referred.toKotlinUuid() }.singleOrNull()?.toReferral()
    }

    /** The referrals of the players [referred], by player; players without one are left out. Joins the transaction it is called in. */
    internal fun JdbcTransaction.referralsOf(referred: Collection<UUID>): Map<UUID, Referral> {
        if (referred.isEmpty()) return emptyMap()
        return ReferralTable.selectAll()
            .where { ReferralTable.referred inList referred.distinct().map { it.toKotlinUuid() } }
            .map { it.toReferral() }
            .associateBy { it.referred }
    }

    /**
     * Adds [millis] of active time to the referral of [referred], which only counts while it isn't complete. Added in the
     * database, not set, so that nothing written by another server in the meantime is lost. Returns the new total, or null
     * when there is no open referral: none, complete, or removed from the count.
     */
    suspend fun addPlaytime(referred: UUID, millis: Long): Long? = dbTransaction {
        val id = referred.toKotlinUuid()
        val open = (ReferralTable.referred eq id) and ReferralTable.redeemed.isNull() and ReferralTable.removed.isNull()
        if (millis > 0) ReferralTable.update({ open }) { it[playtime] = playtime + millis }
        ReferralTable.selectAll().where { open }.singleOrNull()?.get(ReferralTable.playtime)
    }

    /**
     * Completes the referral of [referred], unless it is removed from the count. True for the caller that did it: of two
     * servers racing, only one.
     */
    suspend fun redeem(referred: UUID, now: Long = System.currentTimeMillis()): Boolean = dbTransaction {
        ReferralTable.update({ (ReferralTable.referred eq referred.toKotlinUuid()) and ReferralTable.redeemed.isNull() and ReferralTable.removed.isNull() }) {
            it[redeemed] = now
        } == 1
    }

    /** Takes the referral of [referred] out of the count, as [by]. False when there is none or it is out already. */
    suspend fun remove(referred: UUID, by: String, now: Long = System.currentTimeMillis()): Boolean = dbTransaction {
        ReferralTable.update({ (ReferralTable.referred eq referred.toKotlinUuid()) and ReferralTable.removed.isNull() }) {
            it[removed] = now
            it[removedBy] = by.take(24)
        } == 1
    }

    /** Puts the referral of [referred] back in the count. False when there is none or it isn't removed. */
    suspend fun restore(referred: UUID): Boolean = dbTransaction {
        ReferralTable.update({ (ReferralTable.referred eq referred.toKotlinUuid()) and ReferralTable.removed.isNotNull() }) {
            it[removed] = null
            it[removedBy] = null
        } == 1
    }

    /** Referrals [referrer] got, newest first, [limit] of them from [offset]. */
    suspend fun byReferrer(referrer: UUID, offset: Long, limit: Int): List<ReferralEntry> = dbTransaction {
        val rows = ReferralTable.selectAll()
            .where { ReferralTable.referrer eq referrer.toKotlinUuid() }
            .orderBy(ReferralTable.created, SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { it.toReferral() }
        val names = names(rows.map { it.referred } + referrer)
        rows.map { ReferralEntry(it, names[it.referred] ?: "?", names[referrer] ?: "?") }
    }

    /** How many referrals [referrer] has, complete or not. */
    suspend fun countByReferrer(referrer: UUID): Long = dbTransaction {
        ReferralTable.selectAll().where { ReferralTable.referrer eq referrer.toKotlinUuid() }.count()
    }

    /** Referrals of [referrer] that are still being played for. */
    suspend fun pendingCount(referrer: UUID): Int = dbTransaction {
        ReferralTable.selectAll().where { (ReferralTable.referrer eq referrer.toKotlinUuid()) and ReferralTable.redeemed.isNull() and ReferralTable.removed.isNull() }.count().toInt()
    }

    /** Marks the referral of [referred] as told to the referrer. */
    suspend fun markAnnounced(referred: UUID) {
        dbTransaction { ReferralTable.update({ ReferralTable.referred eq referred.toKotlinUuid() }) { it[announced] = true } }
    }

    /** The names of the players whose completed referrals [referrer] was not told about; marks them told. */
    suspend fun takeUnannounced(referrer: UUID): List<String> = dbTransaction {
        val rows = ReferralTable.selectAll()
            .where { (ReferralTable.referrer eq referrer.toKotlinUuid()) and ReferralTable.redeemed.isNotNull() and ReferralTable.removed.isNull() and (ReferralTable.announced eq false) }
            .map { it.toReferral() }
        if (rows.isEmpty()) return@dbTransaction emptyList()
        ReferralTable.update({ ReferralTable.referred inList rows.map { it.referred.toKotlinUuid() } }) { it[announced] = true }
        val names = names(rows.map { it.referred })
        rows.map { names[it.referred] ?: "?" }
    }

    /** One player's standing. */
    suspend fun standing(uuid: UUID, name: String): Standing = dbTransaction {
        val id = uuid.toKotlinUuid()
        val referrals = ReferralTable.selectAll().where { (ReferralTable.referrer eq id) and ReferralTable.redeemed.isNotNull() and ReferralTable.removed.isNull() }.count().toInt()
        val bonus = BonusTable.selectAll().where { BonusTable.player eq id }.singleOrNull()?.get(BonusTable.amount) ?: 0
        Standing(uuid, name, referrals, bonus)
    }

    /** Everyone with a completed referral or a bonus, most first; equal totals share a place. */
    suspend fun leaderboard(): List<Ranked> = dbTransaction {
        val counted = ReferralTable.referred.count()
        val referrals = ReferralTable.select(ReferralTable.referrer, counted)
            .where { ReferralTable.redeemed.isNotNull() and ReferralTable.removed.isNull() }
            .groupBy(ReferralTable.referrer)
            .associate { it[ReferralTable.referrer].toJavaUuid() to it[counted].toInt() }
        val bonuses = BonusTable.selectAll().associate { it[BonusTable.player].toJavaUuid() to it[BonusTable.amount] }
        val ids = referrals.keys + bonuses.keys
        val names = names(ids)
        rank(ids.map { Standing(it, names[it] ?: "?", referrals[it] ?: 0, bonuses[it] ?: 0) })
    }

    /** Adds [amount] (negative to take) to the bonus of [uuid] and returns the new bonus. */
    suspend fun addBonus(uuid: UUID, name: String, amount: Int): Int = dbTransaction {
        upsertPlayer(uuid, name)
        val id = uuid.toKotlinUuid()
        val updated = BonusTable.update({ BonusTable.player eq id }) { it[BonusTable.amount] = BonusTable.amount + amount }
        if (updated == 0) {
            val inserted = BonusTable.insertIgnore {
                it[player] = id
                it[BonusTable.amount] = amount
            }
            // Another server made the row in the meantime.
            if (inserted.insertedCount == 0) BonusTable.update({ BonusTable.player eq id }) { it[BonusTable.amount] = BonusTable.amount + amount }
        }
        BonusTable.selectAll().where { BonusTable.player eq id }.single()[BonusTable.amount]
    }

    private fun names(uuids: Collection<UUID>): Map<UUID, String> {
        if (uuids.isEmpty()) return emptyMap()
        return PlayerTable.selectAll()
            .where { PlayerTable.uuid inList uuids.distinct().map { it.toKotlinUuid() } }
            .associate { it[PlayerTable.uuid].toJavaUuid() to it[PlayerTable.name] }
    }

    /** [standings] with a total above 0, most first. Equal totals get the place of the first of them, then the next place skips the others. */
    internal fun rank(standings: List<Standing>): List<Ranked> {
        val sorted = standings.filter { it.total > 0 }.sortedWith(compareByDescending<Standing> { it.total }.thenBy { it.name.lowercase() })
        var place = 0
        var previous = Int.MIN_VALUE
        return sorted.mapIndexed { index, standing ->
            if (standing.total != previous) place = index + 1
            previous = standing.total
            Ranked(place, standing)
        }
    }
}
