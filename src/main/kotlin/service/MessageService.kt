package service

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.Message
import discord4j.rest.util.Color
import manager.GuildManager
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.TimeUnit

class MessageService {
    fun sendMessage(event: MessageCreateEvent, message: String): Mono<Void?> {
        return event.message.channel.flatMap { channel -> channel.createMessage(message) }.then()
    }

    fun sendInformationAboutSong(
        event: MessageCreateEvent,
        track: AudioTrack,
        loop: Boolean,
        loopPlaylist: Boolean,
        stayInQueueStatus: Boolean
    ): Mono<Void> {
        val musicManager = GuildManager.getGuildMusicManager(event)

        return Mono.defer {
            if (!stayInQueueStatus) {
                musicManager.scheduler.lastMessage?.delete()?.onErrorResume {
                    Mono.empty()
                } ?: Mono.empty()
            } else {
                Mono.empty()
            }
        }.then(event.message.channel.flatMap {
            createEmbedMessage(
                event,
                if (stayInQueueStatus) "Поставлено в очередь" else "Играющий трек",
                "[${track.info.title}](https://www.youtube.com/watch?v=${track.info.identifier}) - ${track.info.author}",
                track.info.artworkUrl,
                "Трек длиной: ${TimeUnit.MILLISECONDS.toMinutes(track.duration)} минут ${
                    TimeUnit.MILLISECONDS.toSeconds(
                        track.duration
                    ) % 60
                } секунд \n ${if (loop) "Повтор включен" else ""} \n ${if (loopPlaylist) "Повтор плейлиста включен" else ""}"
            ).flatMap { message ->
                if (!stayInQueueStatus) {
                    musicManager.scheduler.lastMessage = message
                }
                Mono.empty<Void>()
            }
        })
    }

    fun createEmbedMessage(
        event: MessageCreateEvent,
        title: String? = null,
        description: String? = null,
        thumbnail: String? = null,
        footer: String? = null,
        color: Color? = null,
        timestamp: Instant? = null,
        image: String? = null,
        author: String? = null,
    ): Mono<Message> {
        return event.message.channel.flatMap { channel ->
            channel.createEmbed { embedCreateSpec ->
                title?.let { embedCreateSpec.setTitle(it) }
                description?.let { embedCreateSpec.setDescription(it) }
                thumbnail?.let { embedCreateSpec.setThumbnail(it) }
                footer?.let { embedCreateSpec.setFooter(it, null) }
                color?.let { embedCreateSpec.setColor(it) }
                timestamp?.let { embedCreateSpec.setTimestamp(it) }
                image?.let { embedCreateSpec.setImage(it) }
                author?.let { embedCreateSpec.setAuthor(it, null, null) }
            }
        }
    }
}