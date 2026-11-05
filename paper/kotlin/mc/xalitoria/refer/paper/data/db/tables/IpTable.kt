package mc.xalitoria.refer.paper.data.db.tables

import mc.xalitoria.refer.paper.data.db.DatabaseManager
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * The log of who connected from where: a row for every address a player has joined from, so that accounts sharing one
 * (alts) can be found. [name] is what the player was called at their last join from it. Looked up by player through the
 * key and by address through the index. Times are milliseconds since the epoch, in UTC.
 */
object IpTable : Table(DatabaseManager.tableName("ip")) {
    val player = reference("player", PlayerTable.uuid, onDelete = ReferenceOption.CASCADE)
    val address = varchar("address", 64).index()
    val name = varchar("name", 24)
    val firstSeen = long("first_seen")
    val lastSeen = long("last_seen")

    override val primaryKey = PrimaryKey(player, address)
}
