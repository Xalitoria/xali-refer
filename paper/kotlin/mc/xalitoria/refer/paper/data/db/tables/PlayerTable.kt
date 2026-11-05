package mc.xalitoria.refer.paper.data.db.tables

import mc.xalitoria.refer.paper.data.db.DatabaseManager
import org.jetbrains.exposed.v1.core.Table

/**
 * Every player seen on a server that shares the database. A referral address is a player's name, so [nameLower] is
 * indexed and finds the player without a scan. Times are milliseconds since the epoch, in UTC.
 */
object PlayerTable : Table(DatabaseManager.tableName("player")) {
    val uuid = uuid("uuid")
    val name = varchar("name", 24)
    val nameLower = varchar("name_lower", 24).index()
    val lastSeen = long("last_seen")

    override val primaryKey = PrimaryKey(uuid)
}
