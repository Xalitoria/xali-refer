package mc.xalitoria.refer.paper.data.config

import com.charleskorn.kaml.YamlComment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import mc.xalitoria.util.config.DatabaseConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class MainConfig(
    val debug: Boolean = false,
    val language: String = "en-us",
    @YamlComment(
        "The address players join with to be referred: <player>.<domain>, such as audizian.xalitoria.com. The domain has to point",
        "at the proxy (or this server) like the main address does, which a wildcard DNS record does.",
    )
    val domain: String = "xalitoria.com",
    val referral: Referral = Referral(),
    @YamlComment("What a referred player has to play before the referral counts, and how that time is measured.")
    val playtime: Playtime = Playtime(),
    val completion: Completion = Completion(),
    val leaderboard: Leaderboard = Leaderboard(),
    @YamlComment(
        "Keeps the addresses players join from, so a person can review accounts that share one: /refer admin log. Alts are allowed;",
        "a reviewer can take a referral out of the count. An address is personal data, so the log is only readable with",
        "xalirefer.log, and turning this off stops recording (what is logged already stays).",
    )
    val log: Log = Log(),
    val database: DatabaseConfig = DatabaseConfig(
        tablePrefix = "xalirefer_",
        sqlite = DatabaseConfig.Sqlite("xalirefer.db"),
        mysql = DatabaseConfig.Mysql(database = "xalirefer"),
    ),
    @YamlComment(
        "Where the address a player joined with comes from. Behind Velocity, the proxy plugin (XaliRefer on Velocity) sends it,",
        "signed with the forwarding secret in Paper's config/paper-global.yml. Without a proxy, turn this off and the server reads",
        "the address from the connection itself.",
    )
    val proxy: Proxy = Proxy(),
) {
    @Serializable
    data class Referral(
        @YamlComment(
            "How long after first joining a player can still be referred, such as 1d or 12h. A player can be referred once, ever.",
            "0s has no limit, which lets players who have been here for years claim a referral.",
        )
        val window: String = "1d",
    ) {
        fun window(): Duration = duration("referral.window", window)
    }

    @Serializable
    data class Playtime(
        @YamlComment("Time the referred player has to be active for. Units: d, h, m, s, and mixes such as 1h 30m.")
        val requirement: String = "2h",
        @YamlComment(
            "Time counts only while the player did something less than this long ago (moved, looked around, chatted, built, clicked",
            "an inventory...). Longer without anything and they are AFK, and the time stops counting until they act again.",
        )
        @SerialName("afk-threshold")
        val afkThreshold: String = "30s",
        @YamlComment("How often the players who have a referral to finish are checked. Not longer than afk-threshold. Short checks cost little: they read a few numbers.")
        @SerialName("check-interval")
        val checkInterval: String = "5s",
        @YamlComment("How often the time earned is written to the database.")
        @SerialName("save-interval")
        val saveInterval: String = "1m",
        val machines: Machines = Machines(),
    ) {
        /**
         * AFK machines (a water stream, a minecart loop, a jump bot, an auto-clicker) keep a player "active" without anyone
         * playing. Time is held back until a window of play has been judged: a window counts only if it shows several
         * kinds of activity and, when the player stayed in a small area, a deliberate one such as chatting or building.
         */
        @Serializable
        data class Machines(
            val enabled: Boolean = true,
            @YamlComment("Length of a window. Time earned in a window that fails is lost, so a longer window is stricter and slower to credit.")
            val window: String = "2m",
            @YamlComment("A player who stays within this many blocks (east-west and north-south) for the whole window is judged as standing still.")
            val radius: Int = 6,
            @YamlComment("How many kinds of activity a window needs: moving keys, looking around, chat, commands, building, clicking, inventory.")
            @SerialName("min-variety")
            val minVariety: Int = 2,
        )

        fun requirement(): Duration = duration("playtime.requirement", requirement)
        fun afkThreshold(): Duration = duration("playtime.afk-threshold", afkThreshold)
        fun checkInterval(): Duration = duration("playtime.check-interval", checkInterval)
        fun saveInterval(): Duration = duration("playtime.save-interval", saveInterval)
        fun machineWindow(): Duration = duration("playtime.machines.window", machines.window)
    }

    @Serializable
    data class Completion(
        @YamlComment(
            "Commands the console runs when a referral is completed, for example \"lp user {referrer} permission settemp ...\" or",
            "\"eco give {referrer} 500\". Placeholders: {player}, {player_uuid} (who was referred), {referrer}, {referrer_uuid}.",
            "They run on the server the player is on when it completes.",
        )
        val commands: List<String> = [],
    )

    @Serializable
    data class Leaderboard(
        @YamlComment("How often /refer top and the placeholders are brought up to date from the database.")
        val refresh: String = "5m",
        @YamlComment("Lines per page of /refer top and /refer list.")
        @SerialName("per-page")
        val perPage: Int = 10,
    ) {
        fun refresh(): Duration = duration("leaderboard.refresh", refresh)
    }

    @Serializable
    data class Log(
        val enabled: Boolean = true,
    )

    @Serializable
    data class Proxy(
        val enabled: Boolean = true,
    )

    /** Fails with the key named when a time is unreadable or negative, so a typo is found when the file loads. */
    fun validate() {
        referral.window()
        val playtime = playtime
        val requirement = playtime.requirement()
        val afk = playtime.afkThreshold()
        val check = playtime.checkInterval()
        playtime.saveInterval()
        playtime.machineWindow()
        require(requirement > Duration.ZERO) { "playtime.requirement must be more than 0s" }
        require(afk > Duration.ZERO) { "playtime.afk-threshold must be more than 0s" }
        require(check >= 1.seconds / 4) { "playtime.check-interval must be at least a quarter of a second" }
        require(check <= afk) { "playtime.check-interval must not be longer than playtime.afk-threshold, or AFK would go unseen" }
        require(playtime.machines.radius >= 1) { "playtime.machines.radius must be at least 1" }
        require(playtime.machines.minVariety in 1..MAX_VARIETY) { "playtime.machines.min-variety must be from 1 to $MAX_VARIETY" }
        leaderboard.refresh()
        require(leaderboard.perPage >= 1) { "leaderboard.per-page must be at least 1" }
        require(domain.isNotBlank() && !domain.startsWith('.')) { "domain must be a host name such as xalitoria.com" }
    }

    companion object {
        /** Kinds of activity there are. */
        const val MAX_VARIETY = 7

        private fun duration(key: String, text: String): Duration {
            val parsed = try {
                Duration.parse(text.trim())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$key: '$text' is not a time. Write it like 30s, 5m, 2h or 1h 30m", e)
            }
            require(!parsed.isNegative() && parsed.isFinite()) { "$key: '$text' must not be negative" }
            return parsed
        }
    }
}
