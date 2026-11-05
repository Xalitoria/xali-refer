package mc.xalitoria.refer.paper.command

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.launch
import mc.xalitoria.refer.paper.XaliRefer
import mc.xalitoria.refer.paper.XaliRefer.Companion.debug
import mc.xalitoria.refer.paper.data.constants.Permissions
import mc.xalitoria.refer.paper.data.db.repository.IpRepository
import mc.xalitoria.refer.paper.data.db.repository.PlayerRepository
import mc.xalitoria.refer.paper.data.db.repository.ReferralRepository
import mc.xalitoria.refer.paper.data.model.Profile
import mc.xalitoria.refer.paper.data.model.Referral
import mc.xalitoria.refer.paper.service.ReferralService
import mc.xalitoria.util.message.fail
import mc.xalitoria.util.message.pass
import mc.xalitoria.util.message.send
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * `/xalirefer`, also `/refer`: what a player sees of their referrals, and the admin commands. Everything that reads the
 * database runs off the region thread and answers when it has the result.
 */
class ReferCommand(private val plugin: XaliRefer) {
    val label = "xalirefer"
    val aliases = listOf("refer", "referral", "xrefer", "xref", "xreferral", "xalireferral")
    val description = "Referrals: share your address, see how your referrals are doing"

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    }

    private val msg get() = plugin.messageConfig.data
    private val service get() = plugin.service

    fun builder(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal(label)
        .requires { it.sender.hasPermission(Permissions.USE) }
        .executes { run(it) { self(it) } }
        .then(Commands.literal("help").executes { run(it) { sender -> sender send msg.commands.help(label, sender.hasPermission(Permissions.ADMIN)) } })
        .then(Commands.literal("ip").executes { run(it) { sender -> withSelf(sender) { player -> sender send msg.info.address(address(player.name)) } } })
        .then(Commands.literal("info")
            .executes { run(it) { sender -> self(sender) } }
            .then(playerArgument().executes { run(it) { sender -> info(sender, it.getArgument("player", String::class.java)) } })
        )
        .then(Commands.literal("top")
            .executes { run(it) { sender -> top(sender, 1) } }
            .then(page().executes { run(it) { sender -> top(sender, it.getArgument("page", Int::class.java)) } })
        )
        .then(Commands.literal("list")
            .executes { run(it) { sender -> withSelf(sender) { player -> list(sender, profile(player), 1) } } }
            .then(page().executes { run(it) { sender -> withSelf(sender) { player -> list(sender, profile(player), it.getArgument("page", Int::class.java)) } } })
        )
        .then(admin())

    private fun admin(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("admin")
        .requires { it.sender.hasPermission(Permissions.ADMIN) }
        .executes { run(it) { sender -> sender send msg.commands.help(label, true) } }
        .then(Commands.literal("give")
            .then(playerArgument()
                .executes { run(it) { sender -> bonus(sender, it.getArgument("player", String::class.java), 1) } }
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                    .executes { run(it) { sender -> bonus(sender, it.getArgument("player", String::class.java), it.getArgument("amount", Int::class.java)) } })
            )
        )
        .then(Commands.literal("take")
            .then(playerArgument()
                .executes { run(it) { sender -> bonus(sender, it.getArgument("player", String::class.java), -1) } }
                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                    .executes { run(it) { sender -> bonus(sender, it.getArgument("player", String::class.java), -it.getArgument("amount", Int::class.java)) } })
            )
        )
        .then(Commands.literal("redeem")
            .then(playerArgument().executes { run(it) { sender -> redeem(sender, it.getArgument("player", String::class.java)) } })
        )
        .then(Commands.literal("list")
            .then(playerArgument()
                .executes { run(it) { sender -> adminList(sender, it.getArgument("player", String::class.java), 1) } }
                .then(page().executes { run(it) { sender -> adminList(sender, it.getArgument("player", String::class.java), it.getArgument("page", Int::class.java)) } })
            )
        )
        .then(log())
        .then(Commands.literal("raffle").executes { run(it) { sender -> raffle(sender) } })
        .then(Commands.literal("save").executes { run(it) { sender -> save(sender) } })
        .then(Commands.literal("reload").executes { run(it) { sender -> reload(sender) } })
        .then(Commands.literal("debug").executes {
            run(it) { sender ->
                val on = plugin.toggleDebug()
                sender pass if (on) msg.commands.debugOn else msg.commands.debugOff
            }
        })

    private fun log(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("log")
        .requires { it.sender.hasPermission(Permissions.LOG) }
        .executes { run(it) { sender -> sender send msg.commands.help(label, true) } }
        .then(Commands.literal("player")
            .then(playerArgument()
                .executes { run(it) { sender -> logPlayer(sender, it.getArgument("player", String::class.java), 1) } }
                .then(page().executes { run(it) { sender -> logPlayer(sender, it.getArgument("player", String::class.java), it.getArgument("page", Int::class.java)) } })
            )
        )
        .then(Commands.literal("alts")
            .then(playerArgument()
                .executes { run(it) { sender -> logAlts(sender, it.getArgument("player", String::class.java), 1) } }
                .then(page().executes { run(it) { sender -> logAlts(sender, it.getArgument("player", String::class.java), it.getArgument("page", Int::class.java)) } })
            )
        )
        .then(Commands.literal("remove")
            .requires { it.sender.hasPermission(Permissions.REVIEW) }
            .then(playerArgument().executes { run(it) { sender -> review(sender, it.getArgument("player", String::class.java), remove = true) } })
        )
        .then(Commands.literal("restore")
            .requires { it.sender.hasPermission(Permissions.REVIEW) }
            .then(playerArgument().executes { run(it) { sender -> review(sender, it.getArgument("player", String::class.java), remove = false) } })
        )
        .then(Commands.literal("ip")
            .then(Commands.argument("address", StringArgumentType.string())
                .suggests { _, builder ->
                    plugin.server.onlinePlayers.mapNotNull { service.addressOf(it) }.filter { it.startsWith(builder.remaining) }.forEach(builder::suggest)
                    builder.buildFuture()
                }
                .executes { run(it) { sender -> logAddress(sender, it.getArgument("address", String::class.java), 1) } }
                .then(page().executes { run(it) { sender -> logAddress(sender, it.getArgument("address", String::class.java), it.getArgument("page", Int::class.java)) } })
            )
        )

    // --- Arguments ------------------------------------------------------------------------------------------

    private fun playerArgument(): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument("player", StringArgumentType.word()).suggests { _, builder ->
            plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(builder.remaining, ignoreCase = true) }.forEach(builder::suggest)
            builder.buildFuture()
        }

    private fun page(): RequiredArgumentBuilder<CommandSourceStack, Int> =
        Commands.argument("page", IntegerArgumentType.integer(1))

    /** Runs [block] for the sender of [context], and tells them if it goes wrong; Brigadier is told it went well either way. */
    private fun run(context: CommandContext<CommandSourceStack>, block: (CommandSender) -> Unit): Int {
        val sender = context.source.sender
        try {
            block(sender)
        } catch (e: Exception) {
            plugin.logger.fail("Failed to run a command of ${sender.name}", e)
            sender fail msg.commands.unexpectedError
        }
        return Command.SINGLE_SUCCESS
    }

    private fun self(sender: CommandSender) = withSelf(sender) { player -> info(sender, player.name) }

    /** Runs [block] with the sender as a player, or tells a console that it has to name one. */
    private fun withSelf(sender: CommandSender, block: (Player) -> Unit) {
        if (sender is Player) block(sender) else sender fail msg.commands.playerOnly
    }

    private fun profile(player: Player) = service.profileOf(player)

    private fun address(name: String) = "$name.${plugin.config.data.domain}"

    /** The player [name] has in the database, or tells [sender] there is none and returns null. */
    private suspend fun find(sender: CommandSender, name: String): Profile? =
        PlayerRepository.byName(name) ?: run { sender fail msg.commands.playerNotFound(name); null }

    /** Runs [block] off the region thread, and reports what goes wrong to [sender]. */
    private fun async(sender: CommandSender, block: suspend () -> Unit) {
        plugin.scope.launch {
            try {
                block()
            } catch (e: Exception) {
                plugin.logger.fail("Failed to run a command of ${sender.name}", e)
                sender fail msg.commands.unexpectedError
            }
        }
    }

    // --- What a player sees ---------------------------------------------------------------------------------

    private fun info(sender: CommandSender, name: String) = async(sender) {
        val profile = find(sender, name) ?: return@async
        val standing = ReferralRepository.standing(profile.uuid, profile.name)
        val pending = ReferralRepository.pendingCount(profile.uuid)
        val position = service.positionOf(profile.uuid)
        sender send msg.info.header(profile.name)
        sender send msg.info.address(address(profile.name))
        sender send msg.info.count(standing.total, standing.referrals, standing.bonus)
        if (position != null) sender send msg.info.position(position)
        if (pending > 0) sender send msg.info.pendingCount(pending)

        // How far the player's own referral is, which is for them to see.
        if (sender is Player && sender.uniqueId == profile.uuid) {
            val referral = ReferralRepository.get(profile.uuid)
            if (referral == null) sender send msg.info.noReferral
            else if (referral.isRemoved) sender send msg.info.referralRemoved
            else if (!referral.isRedeemed) {
                val millis = service.tracker.session(profile.uuid)?.total() ?: referral.playtimeMillis
                val referrer = PlayerRepository.get(referral.referrer)?.name ?: "?"
                sender send msg.info.progress(referrer, service.words(millis / 1000), service.words(plugin.config.data.playtime.requirement().inWholeSeconds))
            }
        }
    }

    private fun top(sender: CommandSender, page: Int) {
        val board = service.leaderboard()
        val perPage = plugin.config.data.leaderboard.perPage
        val pages = max(1, (board.size + perPage - 1) / perPage)
        val shown = page.coerceIn(1, pages)
        sender send msg.info.topHeader(shown, pages)
        if (board.isEmpty()) sender send msg.info.topEmpty
        board.drop((shown - 1) * perPage).take(perPage).forEach {
            sender send msg.info.topEntry(it.position, it.standing.name, it.standing.total)
        }
    }

    private fun list(sender: CommandSender, profile: Profile, page: Int) = async(sender) {
        val perPage = plugin.config.data.leaderboard.perPage
        val count = ReferralRepository.countByReferrer(profile.uuid)
        val pages = max(1, ((count + perPage - 1) / perPage).toInt())
        val shown = page.coerceIn(1, pages)
        val required = plugin.config.data.playtime.requirement().inWholeSeconds
        sender send msg.info.listHeader(profile.name, shown, pages)
        if (count == 0L) sender send msg.info.listEmpty
        for (entry in ReferralRepository.byReferrer(profile.uuid, (shown - 1L) * perPage, perPage)) {
            val referral = entry.referral
            val status = if (referral.isRemoved) msg.info.listRemoved else if (referral.isRedeemed) msg.info.listDone else {
                val millis = service.tracker.session(referral.referred)?.total() ?: referral.playtimeMillis
                msg.info.listProgress(service.words(millis / 1000), service.words(required))
            }
            sender send msg.info.listEntry(entry.referredName, status)
        }
    }

    // --- Admin ----------------------------------------------------------------------------------------------

    private fun adminList(sender: CommandSender, name: String, page: Int) = async(sender) {
        val profile = find(sender, name) ?: return@async
        list(sender, profile, page)
    }

    private fun bonus(sender: CommandSender, name: String, amount: Int) = async(sender) {
        val profile = find(sender, name) ?: return@async
        ReferralRepository.addBonus(profile.uuid, profile.name, amount)
        val total = ReferralRepository.standing(profile.uuid, profile.name).total
        debug("{0} changed the bonus of {1} by {2}", sender.name, profile.name, amount)
        sender pass if (amount > 0) msg.admin.given(profile.name, amount, total) else msg.admin.taken(profile.name, -amount, total)
        service.refreshBoard()
    }

    private fun redeem(sender: CommandSender, name: String) = async(sender) {
        val profile = find(sender, name) ?: return@async
        val referral = ReferralRepository.get(profile.uuid)
        when {
            referral == null -> sender fail msg.admin.notReferred(profile.name)
            referral.isRemoved -> sender fail msg.admin.referralRemoved(profile.name)
            referral.isRedeemed -> sender fail msg.admin.alreadyRedeemed(profile.name)
            service.complete(profile.uuid) -> sender pass msg.admin.redeemed(profile.name)
            else -> sender fail msg.admin.alreadyRedeemed(profile.name)
        }
    }

    // --- The address log -------------------------------------------------------------------------------------

    private fun pagesOf(count: Long, perPage: Int) = max(1, ((count + perPage - 1) / perPage).toInt())

    /** The addresses a player joined from, and how their referral is going. */
    private fun logPlayer(sender: CommandSender, name: String, page: Int) = async(sender) {
        val profile = find(sender, name) ?: return@async
        val perPage = plugin.config.data.leaderboard.perPage
        val pages = pagesOf(IpRepository.countByPlayer(profile.uuid), perPage)
        val shown = page.coerceIn(1, pages)
        val sightings = IpRepository.byPlayer(profile.uuid, (shown - 1L) * perPage, perPage)
        sender send msg.log.playerHeader(profile.name, shown, pages)
        sender send msg.log.referral(status(ReferralRepository.get(profile.uuid)))
        if (sightings.isEmpty()) sender send msg.log.empty
        sightings.forEach { sender send msg.log.playerEntry(it.address, time(it.firstSeen), time(it.lastSeen)) }
        if (!plugin.config.data.log.enabled) sender send msg.log.disabled
    }

    /** The accounts that joined from an address. */
    private fun logAddress(sender: CommandSender, text: String, page: Int) = async(sender) {
        val address = IpRepository.normalize(text)
        if (address == null) {
            sender fail msg.log.badAddress(text)
            return@async
        }
        val perPage = plugin.config.data.leaderboard.perPage
        val pages = pagesOf(IpRepository.countByAddress(address), perPage)
        val shown = page.coerceIn(1, pages)
        val accounts = IpRepository.byAddress(address, (shown - 1L) * perPage, perPage)
        sender send msg.log.ipHeader(address, shown, pages)
        if (accounts.isEmpty()) sender send msg.log.empty
        accounts.forEach { sender send msg.log.accountEntry(it.name, time(it.lastSeen), status(it.referral)) }
    }

    /** The accounts that share an address with a player. */
    private fun logAlts(sender: CommandSender, name: String, page: Int) = async(sender) {
        val profile = find(sender, name) ?: return@async
        val alts = IpRepository.alts(profile.uuid)
        val perPage = plugin.config.data.leaderboard.perPage
        val pages = pagesOf(alts.size.toLong(), perPage)
        val shown = page.coerceIn(1, pages)
        sender send msg.log.altsHeader(profile.name, shown, pages)
        if (alts.isEmpty()) sender send msg.log.empty
        alts.drop((shown - 1) * perPage).take(perPage).forEach {
            sender send msg.log.altEntry(it.name, time(it.lastSeen), status(it.referral), it.addresses.joinToString(", "))
        }
    }

    /** Takes the referral of the referred player [name] out of the count, or puts it back. */
    private fun review(sender: CommandSender, name: String, remove: Boolean) = async(sender) {
        val profile = find(sender, name) ?: return@async
        val referral = ReferralRepository.get(profile.uuid)
        when {
            referral == null -> sender fail msg.admin.notReferred(profile.name)
            remove && referral.isRemoved -> sender fail msg.log.alreadyRemoved(profile.name)
            !remove && !referral.isRemoved -> sender fail msg.log.notRemoved(profile.name)
            remove -> if (service.remove(profile.uuid, sender.name)) sender pass msg.log.removed(profile.name) else sender fail msg.log.alreadyRemoved(profile.name)
            else -> if (service.restore(profile.uuid)) sender pass msg.log.restored(profile.name) else sender fail msg.log.notRemoved(profile.name)
        }
    }

    /** How a referral stands, for the log: not referred, how far it is, or when it was completed and the playtime it took; and who took it out of the count. */
    private fun status(referral: Referral?): String = progress(referral) +
        if (referral?.removed != null) msg.log.statusRemoved(referral.removedBy ?: "?", time(referral.removed)) else ""

    private fun progress(referral: Referral?): String = when {
        referral == null -> msg.log.statusNone
        referral.redeemed != null -> msg.log.statusDone(time(referral.redeemed), service.words(referral.playtimeMillis / 1000))
        else -> {
            val millis = service.tracker.session(referral.referred)?.total() ?: referral.playtimeMillis
            msg.log.statusOpen(service.words(millis / 1000), service.words(plugin.config.data.playtime.requirement().inWholeSeconds))
        }
    }

    private fun time(millis: Long): String = TIME.format(Instant.ofEpochMilli(millis))

    private fun raffle(sender: CommandSender) = async(sender) {
        service.refreshBoard()
        val winner = ReferralService.draw(service.leaderboard())
        if (winner == null) sender fail msg.admin.noWinner else sender pass msg.admin.raffle(winner.name)
    }

    private fun save(sender: CommandSender) = async(sender) {
        sender pass msg.admin.saved(service.saveAll())
    }

    private fun reload(sender: CommandSender) {
        try {
            plugin.reload()
            sender pass msg.commands.reload
        } catch (e: Exception) {
            plugin.logger.fail("Failed to reload the configuration", e)
            sender fail msg.commands.reloadFailed(e.message ?: e.javaClass.simpleName)
        }
    }
}
