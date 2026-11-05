package mc.xalitoria.refer.paper.data.db.repository

import mc.xalitoria.refer.paper.data.db.DatabaseManager.dbTransaction
import mc.xalitoria.refer.paper.data.db.repository.ReferralRepository.referralsOf
import mc.xalitoria.refer.paper.data.db.tables.IpTable
import mc.xalitoria.refer.paper.data.model.Account
import mc.xalitoria.refer.paper.data.model.Sighting
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.net.InetAddress
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/** The address log: which accounts joined from which addresses. See [IpTable]. */
object IpRepository {
    /** The most rows [alts] reads, so an address shared by a whole network can't make a command read the table. */
    const val ALT_ROWS = 1000

    private fun ResultRow.toSighting() = Sighting(
        uuid = this[IpTable.player].toJavaUuid(),
        name = this[IpTable.name],
        address = this[IpTable.address],
        firstSeen = this[IpTable.firstSeen],
        lastSeen = this[IpTable.lastSeen],
    )

    /** [address] as it is logged, see [normalize]. */
    fun normalize(address: InetAddress?): String? = address?.hostAddress?.let(::normalize)

    /**
     * [text] as an address is logged: a literal address in lower case, without an IPv6 zone, an IPv6 one written out the
     * way the server reports it (so `::1` finds `0:0:0:0:0:0:0:1`). Null when it isn't an address.
     */
    fun normalize(text: String): String? {
        val clean = text.trim().substringBefore('%').lowercase()
        if (clean.isEmpty() || clean.length > 45 || !clean.all { it.isLetterOrDigit() || it == '.' || it == ':' }) return null
        // Only a literal with a colon is parsed, which never asks a name server.
        return if (':' in clean) runCatching { InetAddress.getByName(clean).hostAddress.substringBefore('%').lowercase() }.getOrNull() else clean
    }

    /**
     * [uuid], called [name], joined from [address]: the row of the pair is made, or its name and time brought up to date.
     * One write, whether or not the pair is new.
     */
    suspend fun record(uuid: UUID, name: String, address: String, now: Long = System.currentTimeMillis()) {
        dbTransaction {
            val id = uuid.toKotlinUuid()
            val pair = (IpTable.player eq id) and (IpTable.address eq address)
            val refresh = { IpTable.update({ pair }) { it[IpTable.name] = name; it[lastSeen] = now } }
            if (refresh() == 0) {
                val inserted = IpTable.insertIgnore {
                    it[player] = id
                    it[IpTable.address] = address
                    it[IpTable.name] = name
                    it[firstSeen] = now
                    it[lastSeen] = now
                }
                // Another server made the row in the meantime.
                if (inserted.insertedCount == 0) refresh()
            }
        }
    }

    /** The addresses [uuid] joined from, the latest first, [limit] of them from [offset]. */
    suspend fun byPlayer(uuid: UUID, offset: Long, limit: Int): List<Sighting> = dbTransaction {
        IpTable.selectAll()
            .where { IpTable.player eq uuid.toKotlinUuid() }
            .orderBy(IpTable.lastSeen, SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { it.toSighting() }
    }

    suspend fun countByPlayer(uuid: UUID): Long = dbTransaction {
        IpTable.selectAll().where { IpTable.player eq uuid.toKotlinUuid() }.count()
    }

    /** The accounts that joined from [address], the latest first, [limit] of them from [offset]. */
    suspend fun byAddress(address: String, offset: Long, limit: Int): List<Account> = dbTransaction {
        val rows = IpTable.selectAll()
            .where { IpTable.address eq address }
            .orderBy(IpTable.lastSeen, SortOrder.DESC)
            .limit(limit).offset(offset)
            .map { it.toSighting() }
        val referrals = referralsOf(rows.map { it.uuid })
        rows.map { Account(it.uuid, it.name, listOf(it.address), it.lastSeen, referrals[it.uuid]) }
    }

    suspend fun countByAddress(address: String): Long = dbTransaction {
        IpTable.selectAll().where { IpTable.address eq address }.count()
    }

    /**
     * The other accounts that joined from an address [uuid] joined from, the latest first, with the addresses they
     * share. Reads at most [ALT_ROWS] rows.
     */
    suspend fun alts(uuid: UUID): List<Account> = dbTransaction {
        val id = uuid.toKotlinUuid()
        val mine = IpTable.selectAll().where { IpTable.player eq id }.map { it[IpTable.address] }
        if (mine.isEmpty()) return@dbTransaction emptyList()
        val rows = IpTable.selectAll()
            .where { (IpTable.address inList mine) and (IpTable.player neq id) }
            .orderBy(IpTable.lastSeen, SortOrder.DESC)
            .limit(ALT_ROWS)
            .map { it.toSighting() }
        val referrals = referralsOf(rows.map { it.uuid })
        // Rows come latest first, so the first of an account has its latest name and time.
        rows.groupBy { it.uuid }.map { entry ->
            val sightings = entry.value
            Account(entry.key, sightings.first().name, sightings.map { it.address }, sightings.first().lastSeen, referrals[entry.key])
        }
    }
}
