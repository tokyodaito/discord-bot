import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import service.AnalyticsService
import java.util.*
import kotlin.io.path.readText
import kotlin.test.assertTrue

class AnalyticsServiceTest {
    @Test
    fun `log writes message`() {
        val dir = createTempDir()
        val path = dir.toPath().resolve("log.txt")
        val service = AnalyticsService(path)
        val event = mockk<MessageCreateEvent>()
        val message = mockk<Message>()
        val user = mockk<User>()
        every { event.message } returns message
        every { event.guildId } returns Optional.of(Snowflake.of("1"))
        every { message.author } returns Optional.of(user)
        every { user.username } returns "user"
        every { message.content } returns "hi"
        service.log(event).block()
        val text = path.readText()
        assertTrue(text.contains("hi"))
        assertTrue(text.contains("user"))
    }
}
