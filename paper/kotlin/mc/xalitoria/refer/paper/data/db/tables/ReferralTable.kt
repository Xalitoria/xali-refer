package mc.xalitoria.refer.paper.data.db.tables

import mc.xalitoria.refer.paper.data.db.DatabaseManager
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * One row per referred player, which is what makes a player referable once: the row is the claim. [playtime] is the
 * active time in milliseconds, added to by whichever server the player is on; the referral is complete once [redeemed]
 * has a time. A referrer's rows are found through the index on [referrer].
 */
object ReferralTable : Table(DatabaseManager.tableName("referral")) {
    val referred = reference("referred", PlayerTable.uuid, onDelete = ReferenceOption.CASCADE)
    val referrer = reference("referrer", PlayerTable.uuid, onDelete = ReferenceOption.CASCADE).index()
    val created = long("created")
    val playtime = long("playtime").default(0)
    val redeemed = long("redeemed").nullable()

    /**
     * When a reviewer took the referral out of the count, and who ([removedBy], a player's name or the console's). A removed
     * referral is not counted, earns no more time and does not complete until it is restored.
     */
    val removed = long("removed").nullable()
    val removedBy = varchar("removed_by", 24).nullable()

    /** Whether the referrer has been told that it is complete. */
    val announced = bool("announced").default(false)

    override val primaryKey = PrimaryKey(referred)
}
