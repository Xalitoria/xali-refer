import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import mc.xalitoria.refer.paper.data.config.MainConfig
import mc.xalitoria.refer.paper.data.db.DatabaseManager
import mc.xalitoria.refer.paper.data.db.repository.IpRepository
import mc.xalitoria.refer.paper.data.db.repository.PlayerRepository
import mc.xalitoria.refer.paper.data.db.repository.ReferralRepository
import mc.xalitoria.refer.paper.data.db.tables.PlayerTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import java.net.InetAddress
import java.nio.file.Files
import java.util.UUID
import kotlin.uuid.toKotlinUuid
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The address log against a real SQLite file: what is recorded, and how alts are found. */
class AddressLog {
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val carol = UUID.randomUUID()

    @BeforeTest
    fun database() {
        val file = Files.createTempFile("xalirefer-log-test", ".db").also { it.toFile().deleteOnExit() }
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
        }
    }

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    @Test
    fun `addresses are logged as literals in lower case, without a zone`() {
        assertEquals("203.0.113.7", IpRepository.normalize(InetAddress.getByName("203.0.113.7")))
        assertEquals("fe80:0:0:0:1:2:3:4", IpRepository.normalize("FE80:0:0:0:1:2:3:4%eth0"))
        assertEquals("0:0:0:0:0:0:0:1", IpRepository.normalize(" ::1 "))
        assertEquals("1.2.3.4", IpRepository.normalize("::ffff:1.2.3.4"))
        assertNull(IpRepository.normalize("1:2:3"))
        assertNull(IpRepository.normalize("not an address"))
        assertNull(IpRepository.normalize(""))
        assertNull(IpRepository.normalize(null as InetAddress?))
    }

    @Test
    fun `joining again from an address keeps one row, with the first time and the latest name and time`() = run {
        IpRepository.record(alice, "Alice", "203.0.113.7", now = 1_000)
        IpRepository.record(alice, "AliceNew", "203.0.113.7", now = 5_000)
        val rows = IpRepository.byPlayer(alice, 0, 10)
        assertEquals(1, rows.size)
        assertEquals("AliceNew", rows[0].name)
        assertEquals(1_000, rows[0].firstSeen)
        assertEquals(5_000, rows[0].lastSeen)
        assertEquals(1, IpRepository.countByPlayer(alice))
    }

    @Test
    fun `a player's addresses come latest first and in pages`() = run {
        IpRepository.record(alice, "Alice", "10.0.0.1", now = 1)
        IpRepository.record(alice, "Alice", "10.0.0.2", now = 3)
        IpRepository.record(alice, "Alice", "10.0.0.3", now = 2)
        assertEquals(listOf("10.0.0.2", "10.0.0.3"), IpRepository.byPlayer(alice, 0, 2).map { it.address })
        assertEquals(listOf("10.0.0.1"), IpRepository.byPlayer(alice, 2, 2).map { it.address })
        assertEquals(3, IpRepository.countByPlayer(alice))
        assertEquals(0, IpRepository.countByPlayer(bob))
    }

    @Test
    fun `an address lists its accounts with their playtime and when they completed`() = run {
        IpRepository.record(alice, "Alice", "10.0.0.1", now = 1)
        IpRepository.record(bob, "Bob", "10.0.0.1", now = 2)
        ReferralRepository.claim(bob, "Bob", alice)
        ReferralRepository.addPlaytime(bob, 90_000)
        ReferralRepository.redeem(bob, now = 7_000)

        val accounts = IpRepository.byAddress("10.0.0.1", 0, 10)
        assertEquals(listOf("Bob", "Alice"), accounts.map { it.name })
        assertEquals(90_000, accounts[0].referral?.playtimeMillis)
        assertEquals(7_000, accounts[0].referral?.redeemed)
        assertNull(accounts[1].referral)
        assertEquals(2, IpRepository.countByAddress("10.0.0.1"))
        assertEquals(0, IpRepository.byAddress("10.9.9.9", 0, 10).size)
    }

    @Test
    fun `alts are the other accounts on any shared address, with the addresses they share`() = run {
        IpRepository.record(alice, "Alice", "10.0.0.1", now = 1)
        IpRepository.record(alice, "Alice", "10.0.0.2", now = 2)
        IpRepository.record(alice, "Alice", "10.0.0.3", now = 3)
        IpRepository.record(bob, "Bob", "10.0.0.1", now = 10)
        IpRepository.record(bob, "Bob", "10.0.0.2", now = 11)
        IpRepository.record(carol, "Carol", "10.0.0.3", now = 5)
        IpRepository.record(carol, "Carol", "10.9.9.9", now = 6)

        val alts = IpRepository.alts(alice)
        assertEquals(listOf("Bob", "Carol"), alts.map { it.name })
        assertEquals(setOf("10.0.0.1", "10.0.0.2"), alts[0].addresses.toSet())
        assertEquals(listOf("10.0.0.3"), alts[1].addresses)
        assertEquals(11, alts[0].lastSeen)
        // Not the player themself, and nothing for a player with no log.
        assertTrue(alts.none { it.uuid == alice })
        assertEquals(emptyList(), IpRepository.alts(UUID.randomUUID()))
    }

    @Test
    fun `removing a player removes their log`() = run {
        IpRepository.record(alice, "Alice", "10.0.0.1")
        DatabaseManager.dbTransaction {
            PlayerTable.deleteWhere { PlayerTable.uuid eq alice.toKotlinUuid() }
        }
        assertEquals(0, IpRepository.countByAddress("10.0.0.1"))
    }
}
