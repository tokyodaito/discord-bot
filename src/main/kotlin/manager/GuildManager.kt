package manager

import com.sedmelluq.discord.lavaplayer.container.MediaContainer
import com.sedmelluq.discord.lavaplayer.container.MediaContainerRegistry
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.beam.BeamAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.getyarn.GetyarnAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.yamusic.YandexMusicAudioSourceManager
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBufferFactory
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import dev.lavalink.youtube.YoutubeAudioSourceManager
import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import java.util.concurrent.ConcurrentHashMap

object GuildManager {
    val playerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        configuration.frameBufferFactory = AudioFrameBufferFactory { bufferDuration, format, stopping ->
            NonAllocatingAudioFrameBuffer(bufferDuration, format, stopping)
        }

        provideSourceManagers()
    }

    private fun AudioPlayerManager.provideSourceManagers() {
        val youtubeSource = YoutubeAudioSourceManager(/* allowSearch = */ true)

        registerSourceManager(youtubeSource)
        registerSourceManager(YandexMusicAudioSourceManager(true))
        registerSourceManager(SoundCloudAudioSourceManager.createDefault())
        registerSourceManager(BandcampAudioSourceManager())
        registerSourceManager(VimeoAudioSourceManager())
        registerSourceManager(TwitchStreamAudioSourceManager())
        registerSourceManager(BeamAudioSourceManager())
        registerSourceManager(GetyarnAudioSourceManager())
        registerSourceManager(NicoAudioSourceManager())
        registerSourceManager(HttpAudioSourceManager(MediaContainerRegistry(MediaContainer.asList())))
    }

    private val musicManagers = ConcurrentHashMap<Snowflake, GuildMusicManager>()

    fun getGuildMusicManager(guildId: Snowflake): GuildMusicManager {
        return musicManagers.computeIfAbsent(guildId) {
            GuildMusicManager(guildId, playerManager)
        }
    }

    fun getGuildMusicManager(event: MessageCreateEvent): GuildMusicManager? {
        val guildId = event.guildId.orElse(null) ?: return null
        return getGuildMusicManager(guildId)
    }

    fun removeGuildMusicManager(guildId: Snowflake) {
        musicManagers.remove(guildId)?.dispose()
    }
}
