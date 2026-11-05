package mc.xalitoria.refer.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * What the proxy tells the server a player is on: the address the player typed to connect, such as
 * `audizian.xalitoria.com`. The server reads the referrer's name from it.
 */
class Joined(val player: UUID, val host: String) {
    init {
        require(host.length <= Protocol.MAX_HOST) { "A host name has at most ${Protocol.MAX_HOST} characters" }
    }
}

/**
 * Seals a [Joined] for the plugin channel and opens one that arrived.
 *
 * Anything a client can send to a plugin channel reaches the plugins too, and a forged message would credit a referral
 * to whoever the sender names, so each travels signed: the 32-byte HMAC-SHA256 of the message, then the message. The key
 * is Velocity's forwarding secret, which the proxy and every server already share.
 */
object Protocol {
    const val CHANNEL = "xalirefer:main"
    private const val MAC_BYTES = 32

    /** The longest host name there is. */
    const val MAX_HOST = 255

    /** [message] signed with [secret]. */
    fun seal(secret: String, message: Joined): ByteArray {
        val inner = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeLong(message.player.mostSignificantBits)
                writeLong(message.player.leastSignificantBits)
                writeUTF(message.host)
            }
        }.toByteArray()
        return ByteArrayOutputStream().also {
            DataOutputStream(it).apply { write(mac(secret, inner)); write(inner) }
        }.toByteArray()
    }

    /** The message in a [frame]; null when it is unsigned, forged, signed with another secret or cut short. */
    fun open(secret: String?, frame: ByteArray): Joined? {
        val secret = secret.orEmpty()
        if (secret.isBlank() || frame.size <= MAC_BYTES) return null
        val input = DataInputStream(ByteArrayInputStream(frame))
        val mac = input.readNBytes(MAC_BYTES)
        val inner = input.readAllBytes()
        if (!MessageDigest.isEqual(mac, mac(secret, inner))) return null
        return try {
            DataInputStream(ByteArrayInputStream(inner)).run {
                Joined(UUID(readLong(), readLong()), readUTF())
            }
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // Whitespace around the secret is trimmed on both sides, since a secret file usually ends in a newline.
    private fun mac(secret: String, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256")
        .apply { init(SecretKeySpec(secret.trim().toByteArray(), "HmacSHA256")) }
        .doFinal(data)
}
