package mc.xalitoria.refer.paper.data.db.tables

import mc.xalitoria.refer.paper.data.db.DatabaseManager
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/** Referrals an admin gave a player (or took, when [amount] is negative), on top of the ones they earned. */
object BonusTable : Table(DatabaseManager.tableName("bonus")) {
    val player = reference("player", PlayerTable.uuid, onDelete = ReferenceOption.CASCADE)
    val amount = integer("amount").default(0)

    override val primaryKey = PrimaryKey(player)
}
