import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import mc.xalitoria.refer.paper.data.config.MainConfig
import mc.xalitoria.refer.paper.data.db.DatabaseManager
import mc.xalitoria.refer.paper.data.db.repository.Claim
import mc.xalitoria.refer.paper.data.db.repository.PlayerRepository
import mc.xalitoria.refer.paper.data.db.repository.ReferralRepository
import java.nio.file.Files
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The referral rules against a real SQLite file. The tables read the prefix once, so one database serves a class. */
class ReferralStore {
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()
    private val dave = UUID.randomUUID()

    @BeforeTest
    fun database() {
        val file = Files.createTempFile("xalirefer-test", ".db").also { it.toFile().deleteOnExit() }
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:$file"
            driverClassName = "org.sqlite.JDBC"
            maximumPoolSize = 1
            connectionInitSql = "PRAGMA foreign_keys = ON"
        }
        DatabaseManager.attach(HikariDataSource(config), MainConfig().database)
        runBlocking {
            PlayerRepository.seen(alice, "Alice")
            PlayerRepository.seen(bob, "Bob")
            PlayerRepository.seen(carol, "Carol")
            PlayerRepository.seen(dave, "Dave")
        }
    }

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    @Test
    fun `players are found by name in any case, the latest holder of a name first`() = run {
        assertEquals(alice, PlayerRepository.byName("aLiCe")?.uuid)
        assertNull(PlayerRepository.byName("Nobody"))
        val newer = UUID.randomUUID()
        Thread.sleep(2)
        PlayerRepository.seen(newer, "alice")
        assertEquals(newer, PlayerRepository.byName("ALICE")?.uuid)
        PlayerRepository.seen(alice, "AliceRenamed")
        assertEquals("AliceRenamed", PlayerRepository.get(alice)?.name)
    }

    @Test
    fun `a player is referred once, by whoever came first`() = run {
        assertEquals(Claim.Created, ReferralRepository.claim(bob, "Bob", alice))
        assertEquals(Claim.Already(alice), ReferralRepository.claim(bob, "Bob", carol))
        assertEquals(Claim.Already(alice), ReferralRepository.claim(bob, "Bob", alice))
        val referral = assertNotNull(ReferralRepository.get(bob))
        assertEquals(alice, referral.referrer)
        assertEquals(0, referral.playtimeMillis)
        assertFalse(referral.isRedeemed)
    }

    @Test
    fun `a claim makes the row of a player the plugin has not seen`() = run {
        val stranger = UUID.randomUUID()
        assertEquals(Claim.Created, ReferralRepository.claim(stranger, "Stranger", alice))
        assertEquals("Stranger", PlayerRepository.get(stranger)?.name)
    }

    @Test
    fun `playtime adds up in the database and stops when the referral is complete`() = run {
        ReferralRepository.claim(bob, "Bob", alice)
        assertEquals(1000, ReferralRepository.addPlaytime(bob, 1000))
        assertEquals(1500, ReferralRepository.addPlaytime(bob, 500), "added to, not set, so another server's time stays")
        assertEquals(1500, ReferralRepository.addPlaytime(bob, 0))
        assertTrue(ReferralRepository.redeem(bob))
        assertNull(ReferralRepository.addPlaytime(bob, 1000), "nothing counts once it is complete")
        assertEquals(1500, ReferralRepository.get(bob)?.playtimeMillis)
        assertNull(ReferralRepository.addPlaytime(carol, 1000), "carol has no referral")
    }

    @Test
    fun `a referral is completed once, however many ask`() = run {
        ReferralRepository.claim(bob, "Bob", alice)
        assertTrue(ReferralRepository.redeem(bob))
        assertFalse(ReferralRepository.redeem(bob))
        assertFalse(ReferralRepository.redeem(carol))
        assertTrue(ReferralRepository.get(bob)!!.isRedeemed)
    }

    @Test
    fun `a referrer is told of what was completed while away, once`() = run {
        ReferralRepository.claim(bob, "Bob", alice)
        ReferralRepository.claim(carol, "Carol", alice)
        ReferralRepository.redeem(bob)
        assertEquals(listOf("Bob"), ReferralRepository.takeUnannounced(alice))
        assertEquals(emptyList(), ReferralRepository.takeUnannounced(alice))
        ReferralRepository.redeem(carol)
        ReferralRepository.markAnnounced(carol)
        assertEquals(emptyList(), ReferralRepository.takeUnannounced(alice), "already told by the server they were on")
    }

    @Test
    fun `the leaderboard counts completed referrals and bonus, with places shared by equal totals`() = run {
        ReferralRepository.claim(bob, "Bob", alice)
        ReferralRepository.claim(carol, "Carol", alice)
        ReferralRepository.claim(dave, "Dave", alice)
        ReferralRepository.redeem(bob)
        ReferralRepository.redeem(carol)
        // Dave's is still open, so it doesn't count; Bob has a bonus and no referral at all.
        assertEquals(2, ReferralRepository.addBonus(bob, "Bob", 2))
        assertEquals(1, ReferralRepository.addBonus(carol, "Carol", 1))
        assertEquals(0, ReferralRepository.addBonus(carol, "Carol", -1))
        assertEquals(-1, ReferralRepository.addBonus(dave, "Dave", -1))

        val board = ReferralRepository.leaderboard()
        assertEquals(listOf("Alice", "Bob"), board.map { it.standing.name })
        assertEquals(listOf(1, 1), board.map { it.position }, "two each")
        assertEquals(2, board[0].standing.referrals)
        assertEquals(2, board[1].standing.bonus)
        assertEquals(0, ReferralRepository.standing(dave, "Dave").total, "a negative total reads as nothing")
        assertEquals(1, ReferralRepository.pendingCount(alice))
        assertEquals(3, ReferralRepository.countByReferrer(alice))
    }

    @Test
    fun `places skip after a tie`() {
        fun standing(name: String, total: Int) = mc.xalitoria.refer.paper.data.model.Standing(UUID.randomUUID(), name, total, 0)
        val ranked = ReferralRepository.rank(listOf(standing("b", 3), standing("a", 3), standing("c", 2), standing("d", 0)))
        assertEquals(listOf("a" to 1, "b" to 1, "c" to 3), ranked.map { it.standing.name to it.position })
    }

    @Test
    fun `referrals are listed newest first with the names of both`() = run {
        ReferralRepository.claim(bob, "Bob", alice, now = 1000)
        ReferralRepository.claim(carol, "Carol", alice, now = 2000)
        ReferralRepository.claim(dave, "Dave", alice, now = 3000)
        val page = ReferralRepository.byReferrer(alice, offset = 0, limit = 2)
        assertEquals(listOf("Dave", "Carol"), page.map { it.referredName })
        assertEquals("Alice", page.first().referrerName)
        assertEquals(listOf("Bob"), ReferralRepository.byReferrer(alice, offset = 2, limit = 2).map { it.referredName })
    }

    @Test
    fun `a removed referral is out of every count, earns nothing and cannot complete until it is restored`() = run {
        ReferralRepository.claim(bob, "Bob", alice)
        ReferralRepository.claim(carol, "Carol", alice)
        ReferralRepository.addPlaytime(bob, 1000)
        ReferralRepository.redeem(carol)

        assertTrue(ReferralRepository.remove(bob, "Reviewer", now = 5000))
        assertFalse(ReferralRepository.remove(bob, "Someone"), "out already")
        assertFalse(ReferralRepository.remove(dave, "Reviewer"), "dave has no referral")
        val removed = assertNotNull(ReferralRepository.get(bob))
        assertEquals(5000, removed.removed)
        assertEquals("Reviewer", removed.removedBy)
        assertTrue(removed.isRemoved)

        assertNull(ReferralRepository.addPlaytime(bob, 1000), "no more time while it is out")
        assertEquals(1000, ReferralRepository.get(bob)?.playtimeMillis)
        assertFalse(ReferralRepository.redeem(bob), "and it can't complete")
        assertEquals(0, ReferralRepository.pendingCount(alice))

        // A completed one that is removed stops counting, and isn't announced while it is out.
        assertEquals(1, ReferralRepository.standing(alice, "Alice").referrals)
        assertTrue(ReferralRepository.remove(carol, "Reviewer"))
        assertEquals(0, ReferralRepository.standing(alice, "Alice").referrals)
        assertEquals(emptyList(), ReferralRepository.leaderboard())
        assertEquals(emptyList(), ReferralRepository.takeUnannounced(alice))
        assertEquals(2, ReferralRepository.countByReferrer(alice), "still listed")

        assertTrue(ReferralRepository.restore(carol))
        assertFalse(ReferralRepository.restore(carol), "not removed any more")
        assertEquals(1, ReferralRepository.standing(alice, "Alice").referrals)
        assertEquals(listOf("Carol"), ReferralRepository.takeUnannounced(alice))

        assertTrue(ReferralRepository.restore(bob))
        assertNull(ReferralRepository.get(bob)?.removedBy)
        assertEquals(1, ReferralRepository.pendingCount(alice))
        assertEquals(1500, ReferralRepository.addPlaytime(bob, 500), "carries on from where it was")
        assertTrue(ReferralRepository.redeem(bob))
    }
}
