package service

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

class MessageService {
    fun sendMessage(event: MessageCreateEvent, message: String): Mono<Void?> {
        return event.message.channel.flatMap { channel -> channel.createMessage(message) }.then()
    }

    fun sendEmbedMessage(
        event: MessageCreateEvent,
        track: AudioTrack,
        loop: Boolean,
        loopPlaylist: Boolean,
        stayInQueueStatus: Boolean
    ): Mono<Void> {
        val guildId = event.guildId.orElse(null)
        val musicManager = GuildManager.getGuildMusicManager(guildId)

        return Mono.defer {
            if (!stayInQueueStatus) {
                musicManager.scheduler.lastMessage?.delete()?.onErrorResume {
                    Mono.empty()
                } ?: Mono.empty()
            } else {
                Mono.empty()
            }
        }.then(event.message.channel.flatMap { channel ->
            channel.createEmbed { embedCreateSpec ->
                val stayInQueue = if (stayInQueueStatus) "Поставлено в очередь" else "Играющий трек"
                embedCreateSpec.setTitle(stayInQueue)
                embedCreateSpec.setDescription("[${track.info.title}](https://www.youtube.com/watch?v=${track.info.identifier}) - ${track.info.author}")
                embedCreateSpec.setThumbnail(track.info.artworkUrl)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(track.duration)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(track.duration) % 60
                val loopStatus = if (loop) "Повтор включен" else ""
                val loopPlaylistStatus = if (loopPlaylist) "Повтор плейлиста включен" else ""
                embedCreateSpec.setFooter(
                    "Трек длиной: $minutes минут $seconds секунд \n $loopStatus \n $loopPlaylistStatus",
                    null
                )
            }.flatMap { message ->
                if (!stayInQueueStatus) {
                    musicManager.scheduler.lastMessage = message
                }
                Mono.empty<Void>()
            }
        })
    }
}