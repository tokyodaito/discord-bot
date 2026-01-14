import bot.Bot
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.Message
import io.mockk.*
import music.TrackScheduler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.scheduler.VirtualTimeScheduler
import service.GodmodeService
import service.MessageService
import service.VoiceChannelService
import service.music.MusicService
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertEquals

class TrackSchedulerTest {

    private lateinit var messageService: MessageService
    private lateinit var voiceChannelService: VoiceChannelService

    @BeforeEach
    fun setup() {
        messageService = mockk(relaxed = true)
        voiceChannelService = mockk(relaxed = true) {
            every { disconnect(any()) } returns Mono.empty()
        }
        val serviceComponent = object : di.service.ServiceComponent {
            override fun getGodmodeService() = mockk<GodmodeService>(relaxed = true)
            override fun getMessageService() = messageService
            override fun getMusicService() = mockk<MusicService>(relaxed = true)
            override fun getVoiceChannelService() = voiceChannelService
        }
        val remoteComponent = di.remote.RemoteComponent { mockk(relaxed = true) }
        val databaseComponent = object : di.database.DatabaseComponent {
            override fun getDatabase() = mockk<model.database.Database>(relaxed = true)
            override fun getDatabaseImpl() = mockk<model.database.DatabaseImpl>(relaxed = true)
        }
        Bot::class.java.getDeclaredField("serviceComponent").apply { isAccessible = true; set(null, serviceComponent) }
        Bot::class.java.getDeclaredField("remoteComponent").apply { isAccessible = true; set(null, remoteComponent) }
        Bot::class.java.getDeclaredField("databaseComponent").apply { isAccessible = true; set(null, databaseComponent) }
    }

    @AfterEach
    fun tearDown() {
        VirtualTimeScheduler.reset()
    }

    @Test
    fun `playlist loop resets initial playlist before reuse`() {
        val player = mockk<com.sedmelluq.discord.lavaplayer.player.AudioPlayer>(relaxed = true)
        val scheduler = TrackScheduler(player)
        val trackOne = mockk<com.sedmelluq.discord.lavaplayer.track.AudioTrack>(relaxed = true)
        val trackTwo = mockk<com.sedmelluq.discord.lavaplayer.track.AudioTrack>(relaxed = true)

        scheduler.currentTrack = trackOne
        val queueField = TrackScheduler::class.java.getDeclaredField("queue").apply { isAccessible = true }
        val queue = LinkedBlockingQueue<com.sedmelluq.discord.lavaplayer.track.AudioTrack>()
        queue.add(trackTwo)
        queueField.set(scheduler, queue)

        scheduler.playlistLoop = true
        scheduler.playlistLoop = false
        scheduler.playlistLoop = true

        val initialPlaylistField = TrackScheduler::class.java.getDeclaredField("initialPlaylist").apply { isAccessible = true }
        val initialPlaylist = initialPlaylistField.get(scheduler) as MutableList<*>

        assertEquals(2, initialPlaylist.size)
    }

    @Test
    fun `clearQueue releases stored references`() {
        val player = mockk<com.sedmelluq.discord.lavaplayer.player.AudioPlayer>(relaxed = true)
        val scheduler = TrackScheduler(player)
        val event = mockk<MessageCreateEvent>(relaxed = true)
        val message = mockk<Message>(relaxed = true)
        val track = mockk<com.sedmelluq.discord.lavaplayer.track.AudioTrack>(relaxed = true)

        scheduler.currentEvent = event
        scheduler.lastMessage = message
        scheduler.currentTrack = track
        val queueField = TrackScheduler::class.java.getDeclaredField("queue").apply { isAccessible = true }
        val queue = LinkedBlockingQueue<com.sedmelluq.discord.lavaplayer.track.AudioTrack>()
        queue.add(track)
        queueField.set(scheduler, queue)

        scheduler.playlistLoop = true
        scheduler.clearQueue()

        val initialPlaylistField = TrackScheduler::class.java.getDeclaredField("initialPlaylist").apply { isAccessible = true }
        val initialPlaylist = initialPlaylistField.get(scheduler) as MutableList<*>

        assertEquals(null, scheduler.currentEvent)
        assertEquals(null, scheduler.lastMessage)
        assertEquals(0, initialPlaylist.size)
    }

    @Test
    fun `inactivity task cancels when new track starts`() {
        val virtualScheduler = VirtualTimeScheduler.create()

        val player = mockk<com.sedmelluq.discord.lavaplayer.player.AudioPlayer>(relaxed = true)
        val event = mockk<MessageCreateEvent>(relaxed = true)
        val scheduler = TrackScheduler(player, virtualScheduler)
        scheduler.currentEvent = event
        val activeTrack = mockk<com.sedmelluq.discord.lavaplayer.track.AudioTrack>(relaxed = true)
        val queueField = TrackScheduler::class.java.getDeclaredField("queue").apply { isAccessible = true }
        queueField.set(scheduler, LinkedBlockingQueue<com.sedmelluq.discord.lavaplayer.track.AudioTrack>())
        every { player.startTrack(activeTrack, true) } returns true

        scheduler.onTrackEnd(
            player,
            mockk<com.sedmelluq.discord.lavaplayer.track.AudioTrack>(relaxed = true),
            com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason.STOPPED
        )
        scheduler.trackLoaded(activeTrack)

        virtualScheduler.advanceTimeBy(Duration.ofMinutes(5))

        verify(exactly = 0) { voiceChannelService.disconnect(event) }
    }
}
