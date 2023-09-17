package bot

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.voice.AudioProvider
import music.LavaPlayerAudioProvider
import music.TrackScheduler
import reactor.core.publisher.Mono

class GuildMusicManager(
    playerManager: AudioPlayerManager,
    val sendEmbedMessage: (MessageCreateEvent, AudioTrack, Boolean, Boolean, Boolean) -> Mono<Void>
) {
    @Volatile
    internal var godMode = false
    internal val player: AudioPlayer = playerManager.createPlayer()
    internal val scheduler = TrackScheduler(player) { messageCreateEvent, audioTrack, loop, stayInQueue, loopPlaylist ->
        sendEmbedMessage(messageCreateEvent, audioTrack, loop, stayInQueue, loopPlaylist)
    }
    val provider: AudioProvider = LavaPlayerAudioProvider(player)
}