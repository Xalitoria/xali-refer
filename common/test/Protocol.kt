import mc.xalitoria.refer.protocol.Joined
import mc.xalitoria.refer.protocol.Protocol
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProtocolSigning {
    private val player = UUID.randomUUID()

    @Test
    fun `a sealed message opens with the same secret`() {
        val frame = Protocol.seal("secret", Joined(player, "audizian.xalitoria.com"))
        val opened = assertNotNull(Protocol.open("secret\n", frame), "the newline a secret file ends in is not part of the secret")
        assertEquals(player, opened.player)
        assertEquals("audizian.xalitoria.com", opened.host)
    }

    @Test
    fun `a message with another secret, no secret or a changed byte is refused`() {
        val frame = Protocol.seal("secret", Joined(player, "audizian.xalitoria.com"))
        assertNull(Protocol.open("other", frame))
        assertNull(Protocol.open("", frame))
        assertNull(Protocol.open(null, frame))
        assertNull(Protocol.open("secret", frame.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }))
        assertNull(Protocol.open("secret", frame.copyOf(10)))
        assertNull(Protocol.open("secret", ByteArray(0)))
    }

    @Test
    fun `an unsigned message is refused`() {
        // What a client could send: the right fields, but no signature.
        val forged = ByteArray(32) + "audizian.xalitoria.com".toByteArray()
        assertNull(Protocol.open("secret", forged))
    }
}
