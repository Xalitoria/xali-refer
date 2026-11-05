import mc.xalitoria.refer.paper.data.model.Ranked
import mc.xalitoria.refer.paper.data.model.Standing
import mc.xalitoria.refer.paper.service.ReferralService
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferralAddress {
    private fun name(host: String, domain: String = "xalitoria.com") = ReferralService.referrerName(host, domain)

    @Test
    fun `the name is the first part of a subdomain`() {
        assertEquals("audizian", name("audizian.xalitoria.com"))
        assertEquals("audizian", name("Audizian.Xalitoria.COM"))
        assertEquals("audizian", name("audizian.xalitoria.com."))
        assertEquals("audizian", name("audizian.xalitoria.com:25565"))
        assertEquals("a_b1", name("a_b1.xalitoria.com", ".XaliToria.com"))
    }

    @Test
    fun `other addresses name no one`() {
        assertNull(name("xalitoria.com"))
        assertNull(name(".xalitoria.com"))
        assertNull(name("a.b.xalitoria.com"))
        assertNull(name("audizian.example.com"))
        assertNull(name("audizian.notxalitoria.com"))
        assertNull(name("localhost"))
        assertNull(name("1.2.3.4"))
        assertNull(name("bad name.xalitoria.com"))
        assertNull(name("${"a".repeat(25)}.xalitoria.com"))
    }

    private fun ranked(vararg totals: Int) =
        totals.mapIndexed { i, total -> Ranked(i + 1, Standing(UUID.nameUUIDFromBytes(byteArrayOf(i.toByte())), "p$i", total, 0)) }

    @Test
    fun `a raffle gives a ticket per referral`() {
        assertNull(ReferralService.draw(emptyList()))
        assertNull(ReferralService.draw(ranked(0, 0)))
        val entries = ranked(3, 1)
        val wins = IntArray(2)
        val random = Random(42)
        repeat(4000) { wins[if (ReferralService.draw(entries, random)!!.name == "p0") 0 else 1]++ }
        assertTrue(wins[0] in 2800..3200, "the one with 3 of 4 tickets wins about three times in four: ${wins.toList()}")
        assertNotNull(ReferralService.draw(entries))
    }
}
