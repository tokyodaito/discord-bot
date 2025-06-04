import bot.Bot
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.event.domain.message.MessageCreateEvent
import io.mockk.*
import manager.GuildManager
import manager.GuildMusicManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import service.GodmodeService
import service.MessageService
import service.VoiceChannelService
import service.music.MusicService
import java.util.*
import kotlin.test.assertTrue

class GodmodeServiceTest {
    private lateinit var messageService: MessageService
    private lateinit var service: GodmodeService
    private val event = mockk<MessageCreateEvent>(relaxed = true)
    private val message = mockk<Message>(relaxed = true)
    private val user = mockk<User>(relaxed = true)

    @BeforeEach
    fun setup() {
        messageService = mockk(relaxed = true)
        val serviceComponent = object : di.service.ServiceComponent {
            override fun getGodmodeService() = mockk<GodmodeService>(relaxed = true)
            override fun getMessageService() = messageService
            override fun getMusicService() = mockk<MusicService>(relaxed = true)
            override fun getVoiceChannelService() = mockk<VoiceChannelService>(relaxed = true)
        }
        val field = Bot::class.java.getDeclaredField("serviceComponent")
        field.isAccessible = true
        field.set(null, serviceComponent)

        every { event.message } returns message
        every { message.author } returns Optional.of(user)
        every { user.id.asString() } returns GodmodeService.godModeUserId
        every { message.content } returns "!sendmessage hello"

        service = GodmodeService()
    }

    @Test
    fun `authorized user enables godmode`() {
        val musicManager = mockk<GuildMusicManager>(relaxed = true)
        mockkObject(GuildManager)
        every { GuildManager.getGuildMusicManager(event) } returns musicManager

        service.setGodmodeStatus(event, true)

        verify { musicManager.godMode = true }
        verify { messageService.sendMessage(event, "godmode enabled") }
        verify { messageService.sendMessage(event, match { it.contains("gif") }) }
    }

    @Test
    fun `not authorized user denied`() {
        every { user.id.asString() } returns "other"

        service.setGodmodeStatus(event, true)

        verify { messageService.sendMessage(event, match { it.contains("tenor") }) }
    }

    @Test
    fun `sendMessageFromUser sends content`() {
        service.sendMessageFromUser(event)

        verify { messageService.sendMessage(event, "hello") }
    }

    @Test
    fun `isGodModeUser returns true for id`() {
        val method = GodmodeService::class.java.getDeclaredMethod("isGodModeUser", String::class.java)
        method.isAccessible = true
        val result = method.invoke(service, GodmodeService.godModeUserId) as Boolean
        assertTrue(result)
    }
}

