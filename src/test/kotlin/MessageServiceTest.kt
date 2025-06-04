import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo
import discord4j.core.spec.legacy.LegacyEmbedCreateSpec
import discord4j.rest.util.Color
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import service.MessageService
import java.time.Instant
import kotlin.test.assertEquals

class MessageServiceTest {
    private val service = MessageService()


    @Test
    fun `getTrackDescription formats text`() {
        val info = AudioTrackInfo("Song", "Artist", 1000L, "abc", false, "uri")
        val track = mockk<AudioTrack>()
        every { track.info } returns info

        val method = MessageService::class.java.getDeclaredMethod("getTrackDescription", AudioTrack::class.java)
        method.isAccessible = true
        val result = method.invoke(service, track) as String
        assertEquals("[Song](https://www.youtube.com/watch?v=abc) - Artist", result)
    }

    @Test
    fun `getTrackAdditionalInfo formats info`() {
        val track = mockk<AudioTrack>()
        every { track.duration } returns 125000L

        val method = MessageService::class.java.getDeclaredMethod(
            "getTrackAdditionalInfo",
            AudioTrack::class.java,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        val result = method.invoke(service, track, true, false) as String
        assertEquals("Трек длиной: 2 минут 5 секунд \n Повтор включен \n ", result)
    }

    @Test
    fun `getStatus returns proper text`() {
        val method = MessageService::class.java.getDeclaredMethod("getStatus", Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        val stay = method.invoke(service, true) as String
        val play = method.invoke(service, false) as String
        assertEquals("Поставлено в очередь", stay)
        assertEquals("Играющий трек", play)
    }

    @Test
    fun `applyEmbedProperties sets values`() {
        val embed = mockk<LegacyEmbedCreateSpec>(relaxed = true)
        val method = MessageService::class.java.getDeclaredMethod(
            "applyEmbedProperties",
            LegacyEmbedCreateSpec::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Color::class.java,
            Instant::class.java,
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        val time = Instant.EPOCH
        method.invoke(service, embed, "t", "d", "thumb", "foot", Color.RED, time, "img", "auth")

        verify {
            embed.setTitle("t")
            embed.setDescription("d")
            embed.setThumbnail("thumb")
            embed.setFooter("foot", null)
            embed.setColor(Color.RED)
            embed.setTimestamp(time)
            embed.setImage("img")
            embed.setAuthor("auth", null, null)
        }
    }
}
