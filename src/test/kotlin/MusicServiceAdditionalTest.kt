import bot.Bot
import discord4j.core.event.domain.message.MessageCreateEvent
import io.mockk.*
import manager.GuildManager
import manager.GuildMusicManager
import music.TrackScheduler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import service.GodmodeService
import service.MessageService
import service.VoiceChannelService
import service.music.MusicService
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MusicServiceAdditionalTest {

    private lateinit var messageService: MessageService
    private lateinit var service: MusicService

    @BeforeEach
    fun setup() {
        messageService = mockk(relaxed = true)
        val serviceComponent = object : di.service.ServiceComponent {
            override fun getGodmodeService() = mockk<GodmodeService>(relaxed = true)
            override fun getMessageService() = messageService
            override fun getMusicService() = mockk<MusicService>(relaxed = true)
            override fun getVoiceChannelService() = mockk<VoiceChannelService>(relaxed = true)
        }
        val remoteComponent = di.remote.RemoteComponent { mockk(relaxed = true) }
        val databaseComponent = object : di.database.DatabaseComponent {
            override fun getDatabase() = mockk<model.database.Database>(relaxed = true)
            override fun getDatabaseImpl() = mockk<model.database.DatabaseImpl>(relaxed = true)
        }
        Bot::class.java.getDeclaredField("serviceComponent").apply { isAccessible = true; set(null, serviceComponent) }
        Bot::class.java.getDeclaredField("remoteComponent").apply { isAccessible = true; set(null, remoteComponent) }
        Bot::class.java.getDeclaredField("databaseComponent").apply { isAccessible = true; set(null, databaseComponent) }
        service = MusicService()
    }
    @Test
    fun `isValidYoutubeLink detects urls`() {
        val method = MusicService::class.java.getDeclaredMethod("isValidYoutubeLink", String::class.java)
        method.isAccessible = true
        val valid = method.invoke(service, "https://youtu.be/test") as Boolean
        val invalid = method.invoke(service, "bad link") as Boolean
        assertTrue(valid)
        assertFalse(invalid)
    }

    @Test
    fun `nextTrack moves when multiple tracks`() {
        val scheduler = mockk<TrackScheduler>(relaxed = true)
        var loopVal = true
        every { scheduler.loop } answers { loopVal }
        every { scheduler.loop = any() } answers { loopVal = firstArg() }
        every { scheduler.getFullTrackList() } returns listOf(mockk(), mockk())
        val musicManager = mockk<GuildMusicManager>(relaxed = true) {
            every { this@mockk.scheduler } returns scheduler
        }
        val event = mockk<MessageCreateEvent>(relaxed = true)
        mockkObject(GuildManager)
        every { GuildManager.getGuildMusicManager(event) } returns musicManager

        val result = service.nextTrack(event)

        assertTrue(result)
        assertFalse(loopVal)
        verify { scheduler.nextTrack() }
    }

    @Test
    fun `nextTrack returns false for single track`() {
        val scheduler = mockk<TrackScheduler>(relaxed = true)
        every { scheduler.getFullTrackList() } returns listOf(mockk())
        val musicManager = mockk<GuildMusicManager>(relaxed = true) {
            every { this@mockk.scheduler } returns scheduler
        }
        val event = mockk<MessageCreateEvent>(relaxed = true)
        mockkObject(GuildManager)
        every { GuildManager.getGuildMusicManager(event) } returns musicManager

        val result = service.nextTrack(event)

        assertFalse(result)
        verify(exactly = 0) { scheduler.nextTrack() }
    }
}
