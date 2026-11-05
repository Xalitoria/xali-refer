package mc.xalitoria.refer.paper.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mc.xalitoria.util.message.formatArgs

/** Every message the plugin sends, in `language/<language>.yml`. `{0}`, `{1}`... are filled in by the plugin. */
@Serializable
data class MessageConfig(
    val commands: Commands = Commands(),
    val referral: Referral = Referral(),
    val info: Info = Info(),
    val admin: Admin = Admin(),
    val log: Log = Log(),
    val words: Words = Words(),
) {
    @Serializable
    data class Commands(
        @SerialName("no-permission")
        val noPermission: String = "You don't have permission to use this command.",
        @SerialName("player-only")
        val playerOnly: String = "You must be a player to use this command.",
        @SerialName("player-not-found")
        private val playerNotFound: String = "No player called {0} has joined.",
        @SerialName("not-a-number")
        private val notANumber: String = "{0} is not a number.",
        private val usage: String = "Usage: {0}",
        private val help: String = """
            {#80f>}&lXALI{#88f<}&f&lREFER &7> &dHELP
            &d/{0} &r- your referral address and how your referrals are doing
            &d/{0} &rinfo [player] &7- the same for another player
            &d/{0} &rip &7- the address to share
            &d/{0} &rtop [page] &7- who referred the most players
            &d/{0} &rlist [page] &7- the players you referred and how far they are
        """.trimIndent(),
        private val adminHelp: String = """
            &d/{0} &radmin give|take <player> [amount] &7- change a player's bonus referrals
            &d/{0} &radmin redeem <player> &7- complete a player's referral now
            &d/{0} &radmin raffle &7- draw a player, with a ticket for every referral
            &d/{0} &radmin log player|alts <player> [page] &7- the addresses a player joined from, and the accounts sharing them
            &d/{0} &radmin log ip <address> [page] &7- the accounts that joined from an address (quote an IPv6 one)
            &d/{0} &radmin log remove|restore <player> &7- take a player's referral out of the count, or put it back
            &d/{0} &radmin save &7- write the progress of players online to the database
            &d/{0} &radmin reload|debug
        """.trimIndent(),
        val reload: String = "Configuration reloaded.",
        @SerialName("reload-failed")
        private val reloadFailed: String = "The configuration was not reloaded: {0}",
        @SerialName("debug-on")
        val debugOn: String = "Debug logging is on.",
        @SerialName("debug-off")
        val debugOff: String = "Debug logging is off.",
        @SerialName("unexpected-error")
        val unexpectedError: String = "An unexpected error occurred. Please try again.",
    ) {
        fun playerNotFound(name: String): String = playerNotFound.formatArgs(name)
        fun notANumber(text: String): String = notANumber.formatArgs(text)
        fun usage(text: String): String = usage.formatArgs(text)
        fun help(label: String, admin: Boolean): String =
            help.formatArgs(label) + if (admin) "\n" + adminHelp.formatArgs(label) else ""
        fun reloadFailed(reason: String): String = reloadFailed.formatArgs(reason)
    }

    @Serializable
    data class Referral(
        private val joined: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> You joined with {0}'s referral. Play for {1} and they get a point!",
        @SerialName("unknown-referrer")
        private val unknownReferrer: String = "&c{0} has not played here, so the referral doesn't count.",
        val self: String = "&cYou can't use your own referral.",
        @SerialName("too-late")
        private val tooLate: String = "&cReferrals only count for players who joined in the last {0}.",
        @SerialName("already-referred")
        val alreadyReferred: String = "&cYou have been referred already.",
        private val completed: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> &aYou have redeemed &f{0}&a's referral!",
        @SerialName("completed-referrer")
        private val completedReferrer: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> &f{0} &ahas redeemed your referral! You have &f{1}&a.",
        @SerialName("completed-while-away")
        private val completedWhileAway: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> &aWhile you were away &f{0} &areferrals were redeemed: &f{1}",
    ) {
        fun joined(referrer: String, requirement: String): String = joined.formatArgs(referrer, requirement)
        fun unknownReferrer(name: String): String = unknownReferrer.formatArgs(name)
        fun tooLate(window: String): String = tooLate.formatArgs(window)
        fun completed(referrer: String): String = completed.formatArgs(referrer)
        fun completedReferrer(player: String, total: String): String = completedReferrer.formatArgs(player, total)
        fun completedWhileAway(count: Int, players: String): String = completedWhileAway.formatArgs(count, players)
    }

    @Serializable
    data class Info(
        private val header: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> &d{0}",
        private val address: String = "&dAddress: &f{0}",
        private val count: String = "&dReferrals: &f{0} &7({1} from players, {2} bonus)",
        private val position: String = "&dPosition: &f#{0}",
        @SerialName("pending-count")
        private val pendingCount: String = "&dStill playing for it: &f{0}",
        private val progress: String = "&dYour referral by {0}: &f{1}&7/{2}",
        @SerialName("no-referral")
        val noReferral: String = "&7No one referred you.",
        @SerialName("top-header")
        private val topHeader: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> &dTop referrers &7({0}/{1})",
        @SerialName("top-entry")
        private val topEntry: String = "&d#{0} &f{1} &7- {2}",
        @SerialName("top-empty")
        val topEmpty: String = "&7Nobody has referred anyone yet.",
        @SerialName("list-header")
        private val listHeader: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> &d{0}'s referrals &7({1}/{2})",
        @SerialName("list-entry")
        private val listEntry: String = "  &f{0} &7- {1}",
        @SerialName("list-done")
        val listDone: String = "&aDone",
        @SerialName("list-progress")
        private val listProgress: String = "&7{0}/{1}",
        @SerialName("list-removed")
        val listRemoved: String = "&cNot counted",
        @SerialName("referral-removed")
        val referralRemoved: String = "&cYour referral is not being counted.",
        @SerialName("list-empty")
        val listEmpty: String = "&7No referrals.",
    ) {
        fun header(name: String): String = header.formatArgs(name)
        fun address(address: String): String = this.address.formatArgs(address)
        fun count(total: Int, referrals: Int, bonus: Int): String = count.formatArgs(total, referrals, bonus)
        fun position(position: Int): String = this.position.formatArgs(position)
        fun pendingCount(count: Int): String = pendingCount.formatArgs(count)
        fun progress(referrer: String, done: String, required: String): String = progress.formatArgs(referrer, done, required)
        fun topHeader(page: Int, pages: Int): String = topHeader.formatArgs(page, pages)
        fun topEntry(position: Int, name: String, count: Int): String = topEntry.formatArgs(position, name, count)
        fun listHeader(name: String, page: Int, pages: Int): String = listHeader.formatArgs(name, page, pages)
        fun listEntry(name: String, status: String): String = listEntry.formatArgs(name, status)
        fun listProgress(done: String, required: String): String = listProgress.formatArgs(done, required)
    }

    @Serializable
    data class Admin(
        private val given: String = "&aGave {0} &f{1}&a bonus referrals. They have &f{2}&a.",
        private val taken: String = "&aTook &f{1}&a bonus referrals from {0}. They have &f{2}&a.",
        private val redeemed: String = "&aCompleted the referral of {0}.",
        @SerialName("not-referred")
        private val notReferred: String = "&c{0} has no referral to complete.",
        @SerialName("already-redeemed")
        private val alreadyRedeemed: String = "&c{0}'s referral is complete already.",
        private val raffle: String = "&bThe winner of the raffle is &d{0}&b!",
        @SerialName("referral-removed")
        private val referralRemoved: String = "&c{0}'s referral is removed from the count. Restore it first.",
        @SerialName("no-winner")
        val noWinner: String = "&cNo one has a referral to draw.",
        private val saved: String = "&aSaved the progress of {0} players.",
    ) {
        fun given(player: String, amount: Int, total: Int): String = given.formatArgs(player, amount, total)
        fun taken(player: String, amount: Int, total: Int): String = taken.formatArgs(player, amount, total)
        fun redeemed(player: String): String = redeemed.formatArgs(player)
        fun referralRemoved(player: String): String = referralRemoved.formatArgs(player)
        fun notReferred(player: String): String = notReferred.formatArgs(player)
        fun alreadyRedeemed(player: String): String = alreadyRedeemed.formatArgs(player)
        fun raffle(player: String): String = raffle.formatArgs(player)
        fun saved(count: Int): String = saved.formatArgs(count)
    }

    /** `/refer admin log`. A time is written `yyyy-MM-dd HH:mm` in the server's time zone. */
    @Serializable
    data class Log(
        @SerialName("player-header")
        private val playerHeader: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> &d{0}'s addresses &7({1}/{2})",
        @SerialName("ip-header")
        private val ipHeader: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> &dAccounts on {0} &7({1}/{2})",
        @SerialName("alts-header")
        private val altsHeader: String = "{#80f>}&lXALI{#88f<}&f&lREFER &7> &d{0}'s alts &7({1}/{2})",
        @SerialName("player-entry")
        private val playerEntry: String = "  &f{0} &7- first {1}, last {2}",
        @SerialName("account-entry")
        private val accountEntry: String = "  &f{0} &7- last {1} - {2}",
        @SerialName("alt-entry")
        private val altEntry: String = "  &f{0} &7- last {1} - {2} &7- on &f{3}",
        @SerialName("referral")
        private val referral: String = "&dReferral: &f{0}",
        @SerialName("status-none")
        val statusNone: String = "&7not referred",
        @SerialName("status-open")
        private val statusOpen: String = "&e{0}/{1} played",
        @SerialName("status-done")
        private val statusDone: String = "&acompleted {0} after {1}",
        @SerialName("status-removed")
        private val statusRemoved: String = " &c[removed by {0}, {1}]",
        private val removed: String = "&aTook {0}'s referral out of the count.",
        private val restored: String = "&aPut {0}'s referral back in the count.",
        @SerialName("already-removed")
        private val alreadyRemoved: String = "&c{0}'s referral is out of the count already.",
        @SerialName("not-removed")
        private val notRemoved: String = "&c{0}'s referral is not removed.",
        val empty: String = "&7Nothing logged.",
        @SerialName("bad-address")
        private val badAddress: String = "&c{0} is not an address.",
        val disabled: String = "&7Logging is off in the configuration, so nothing new is recorded.",
    ) {
        fun playerHeader(name: String, page: Int, pages: Int): String = playerHeader.formatArgs(name, page, pages)
        fun ipHeader(address: String, page: Int, pages: Int): String = ipHeader.formatArgs(address, page, pages)
        fun altsHeader(name: String, page: Int, pages: Int): String = altsHeader.formatArgs(name, page, pages)
        fun playerEntry(address: String, first: String, last: String): String = playerEntry.formatArgs(address, first, last)
        fun accountEntry(name: String, last: String, status: String): String = accountEntry.formatArgs(name, last, status)
        fun altEntry(name: String, last: String, status: String, addresses: String): String = altEntry.formatArgs(name, last, status, addresses)
        fun referral(status: String): String = referral.formatArgs(status)
        fun statusOpen(done: String, required: String): String = statusOpen.formatArgs(done, required)
        fun statusDone(time: String, played: String): String = statusDone.formatArgs(time, played)
        fun statusRemoved(by: String, time: String): String = statusRemoved.formatArgs(by, time)
        fun removed(player: String): String = removed.formatArgs(player)
        fun restored(player: String): String = restored.formatArgs(player)
        fun alreadyRemoved(player: String): String = alreadyRemoved.formatArgs(player)
        fun notRemoved(player: String): String = notRemoved.formatArgs(player)
        fun badAddress(text: String): String = badAddress.formatArgs(text)
    }

    /** The units of a time such as "1 hour 5 minutes". */
    @Serializable
    data class Words(
        val day: String = "day",
        val days: String = "days",
        val hour: String = "hour",
        val hours: String = "hours",
        val minute: String = "minute",
        val minutes: String = "minutes",
        val second: String = "second",
        val seconds: String = "seconds",
        val unknown: String = "unknown",
    ) {
        /** [totalSeconds] as words, such as "1 hour 5 minutes"; "0 seconds" for nothing. */
        fun duration(totalSeconds: Long): String {
            var left = totalSeconds.coerceAtLeast(0)
            val parts = ArrayList<String>(4)
            for ([size, one, many] in units) {
                val count = left / size
                left %= size
                if (count > 0) parts += "$count ${if (count == 1L) one else many}"
            }
            return parts.joinToString(" ").ifEmpty { "0 $seconds" }
        }

        private val units: List<Triple<Long, String, String>>
            get() = [Triple(86_400L, day, days), Triple(3_600L, hour, hours), Triple(60L, minute, minutes), Triple(1L, second, seconds)]
    }
}
