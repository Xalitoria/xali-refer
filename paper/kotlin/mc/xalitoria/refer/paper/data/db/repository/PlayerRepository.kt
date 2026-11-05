package mc.xalitoria.refer.paper.data.db.repository

import mc.xalitoria.refer.paper.data.db.DatabaseManager.dbTransaction
import mc.xalitoria.refer.paper.data.db.tables.PlayerTable
import mc.xalitoria.refer.paper.data.model.Profile
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

object PlayerRepository {
    private fun ResultRow.toProfile() = Profile(this[PlayerTable.uuid].toJavaUuid(), this[PlayerTable.name])

    /** Makes the row of the player [uuid], or brings their name and time up to date. Joins the transaction it is called in. */
    internal fun JdbcTransaction.upsertPlayer(uuid: UUID, name: String) {
        val id = uuid.toKotlinUuid()
        val now = System.currentTimeMillis()
        val updated = PlayerTable.update({ PlayerTable.uuid eq id }) {
            it[PlayerTable.name] = name
            it[nameLower] = name.lowercase()
            it[lastSeen] = now
        }
        if (updated == 0) PlayerTable.insert {
            it[PlayerTable.uuid] = id
            it[PlayerTable.name] = name
            it[nameLower] = name.lowercase()
            it[lastSeen] = now
        }
    }

    /** The player arrives or is seen again: their row is made, or kept up to date with their name and the time. */
    suspend fun seen(uuid: UUID, name: String) {
        dbTransaction { upsertPlayer(uuid, name) }
    }

    suspend fun get(uuid: UUID): Profile? = dbTransaction {
        PlayerTable.selectAll().where { PlayerTable.uuid eq uuid.toKotlinUuid() }.singleOrNull()?.toProfile()
    }

    /** A player by name in any case. The one seen last, if a name has changed hands. */
    suspend fun byName(name: String): Profile? = dbTransaction {
        PlayerTable.selectAll()
            .where { PlayerTable.nameLower eq name.lowercase() }
            .orderBy(PlayerTable.lastSeen, SortOrder.DESC)
            .limit(1)
            .firstOrNull()?.toProfile()
    }

    suspend fun getAll(uuids: Collection<UUID>): Map<UUID, Profile> {
        if (uuids.isEmpty()) return emptyMap()
        return dbTransaction {
            PlayerTable.selectAll()
                .where { PlayerTable.uuid inList uuids.map { it.toKotlinUuid() } }
                .map { it.toProfile() }
                .associateBy { it.uuid }
        }
    }
}
