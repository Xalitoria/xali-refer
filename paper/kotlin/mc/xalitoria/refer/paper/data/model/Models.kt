package mc.xalitoria.refer.paper.data.model

import java.util.UUID

/** A player the plugin has seen. */
data class Profile(val uuid: UUID, val name: String)

/**
 * A player's referral: [referred] joined with [referrer]'s address. [playtimeMillis] is the active time they have
 * earned towards it, and it is complete once [redeemed] is set (milliseconds since the epoch).
 */
data class Referral(
    val referred: UUID,
    val referrer: UUID,
    val created: Long,
    val playtimeMillis: Long,
    val redeemed: Long?,
    /** When a reviewer took it out of the count, and who did. */
    val removed: Long? = null,
    val removedBy: String? = null,
    /** Whether the referrer has been told it is complete. */
    val announced: Boolean,
) {
    val isRedeemed: Boolean get() = redeemed != null
    val isRemoved: Boolean get() = removed != null
}

/** A referral with the names of both players, for lists. */
data class ReferralEntry(val referral: Referral, val referredName: String, val referrerName: String)

/** What a player has: the referrals that were completed, and the ones an admin gave or took. */
data class Standing(val uuid: UUID, val name: String, val referrals: Int, val bonus: Int) {
    val total: Int get() = (referrals + bonus).coerceAtLeast(0)
}

/** A [Standing] with the place it holds; equal totals share a place. */
data class Ranked(val position: Int, val standing: Standing)

/** An address [uuid] (called [name] then) joined from, between [firstSeen] and [lastSeen]. */
data class Sighting(val uuid: UUID, val name: String, val address: String, val firstSeen: Long, val lastSeen: Long)

/**
 * An account that joined from [addresses], last at [lastSeen], with its [referral] if it was referred. The playtime and
 * the time it was completed are the referral's. [addresses] is one address when listing an address, the shared ones when
 * looking for alts.
 */
data class Account(val uuid: UUID, val name: String, val addresses: List<String>, val lastSeen: Long, val referral: Referral?)
