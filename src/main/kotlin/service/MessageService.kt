package service

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.Message
import discord4j.core.spec.legacy.LegacyEmbedCreateSpec
import discord4j.rest.util.Color
import manager.GuildManager
import manager.GuildMusicManager
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class MessageService {
    fun sendMessage(event: MessageCreateEvent, message: String): Mono<Void?> {
        return event.message.channel.flatMap { channel -> channel.createMessage(message) }.then()
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(5))).onErrorResume {
                println("Error sendMessage: $it")
                Mono.empty()
            }
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
                applyEmbedProperties(
                    embedCreateSpec, title, description, thumbnail, footer, color, timestamp, image, author
                )
            }
        }.retryWhen(Retry.backoff(3, Duration.ofSeconds(5))).onErrorResume {
            println("Error sendMessage: $it")
            Mono.empty()
        }
    }

    fun editEmbedMessage(
        message: Message,
        title: String? = null,
        description: String? = null,
        thumbnail: String? = null,
        footer: String? = null,
        color: Color? = null,
        timestamp: Instant? = null,
        image: String? = null,
        author: String? = null,
    ): Mono<Message> {
        return message.edit { messageEditSpec ->
            messageEditSpec.setEmbed { embedCreateSpec ->
                applyEmbedProperties(
                    embedCreateSpec, title, description, thumbnail, footer, color, timestamp, image, author
                )
            }
        }.retryWhen(Retry.backoff(3, Duration.ofSeconds(5))).onErrorResume {
            println("Error sendMessage: $it")
            Mono.empty()
        }
    }

    fun sendNewInformationAboutSong(
        event: MessageCreateEvent, track: AudioTrack, loop: Boolean,
        loopPlaylist: Boolean, stayInQueueStatus: Boolean
    ): Mono<Void> {
        val musicManager = GuildManager.getGuildMusicManager(event)

        return Mono.defer {
            synchronized(musicManager.scheduler.lastMessageLock) {
                val lastMessage = musicManager.scheduler.lastMessage
                if (lastMessage != null && !stayInQueueStatus) {
                    lastMessage.delete().subscribe()
                }
                createMessage(event, track, loop, loopPlaylist, stayInQueueStatus, musicManager)
            }
        }
    }

    fun sendInformationAboutSong(
        event: MessageCreateEvent, track: AudioTrack, loop: Boolean,
        loopPlaylist: Boolean, stayInQueueStatus: Boolean
    ): Mono<Void> {
        val musicManager = GuildManager.getGuildMusicManager(event)

        return Mono.defer {
            synchronized(musicManager.scheduler.lastMessageLock) {
                val lastMessage = musicManager.scheduler.lastMessage
                if (lastMessage != null && !stayInQueueStatus) {
                    editMessage(lastMessage, track, loop, loopPlaylist, stayInQueueStatus)
                } else {
                    createMessage(event, track, loop, loopPlaylist, stayInQueueStatus, musicManager)
                }
            }
        }
    }

    private fun editMessage(
        lastMessage: Message,
        track: AudioTrack,
        loop: Boolean,
        loopPlaylist: Boolean,
        stayInQueueStatus: Boolean
    ): Mono<Void> {
        return editEmbedMessage(
            lastMessage,
            getStatus(stayInQueueStatus),
            getTrackDescription(track),
            "https://static.wikia.nocookie.net/all-worlds-alliance/images/2/24/9abc7cf4bd20d565c5f7da6df73a9bdf.png/revision/latest?cb=20190106111029",
            getTrackAdditionalInfo(track, loop, loopPlaylist)
        ).doOnError {
            println("Error editing message: $it")
        }.then()
    }

    private fun createMessage(
        event: MessageCreateEvent,
        track: AudioTrack,
        loop: Boolean,
        loopPlaylist: Boolean,
        stayInQueueStatus: Boolean,
        musicManager: GuildMusicManager
    ): Mono<Void> {
        return event.message.channel.flatMap {
            createEmbedMessage(
                event,
                getStatus(stayInQueueStatus),
                getTrackDescription(track),
                "https://static.wikia.nocookie.net/all-worlds-alliance/images/2/24/9abc7cf4bd20d565c5f7da6df73a9bdf.png/revision/latest?cb=20190106111029",
                getTrackAdditionalInfo(track, loop, loopPlaylist)
            ).flatMap { message ->
                synchronized(musicManager.scheduler.lastMessageLock) {
                    if (!stayInQueueStatus) {
                        musicManager.scheduler.lastMessage = message
                    }
                }
                Mono.empty<Void>()
            }.onErrorResume {
                println("Error creating message: $it")
                Mono.empty()
            }
        }
    }

    private fun getTrackDescription(track: AudioTrack): String {
        return "[${track.info.title}](https://www.youtube.com/watch?v=${track.info.identifier}) - ${track.info.author}"
    }

    private fun getTrackAdditionalInfo(track: AudioTrack, loop: Boolean, loopPlaylist: Boolean): String {
        val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(track.duration)
        val durationSeconds = TimeUnit.MILLISECONDS.toSeconds(track.duration) % 60
        val loopStatus = if (loop) "Повтор включен" else ""
        val loopPlaylistStatus = if (loopPlaylist) "Повтор плейлиста включен" else ""
        return "Трек длиной: $durationMinutes минут $durationSeconds секунд \n $loopStatus \n $loopPlaylistStatus"
    }

    private fun getStatus(stayInQueueStatus: Boolean): String {
        return if (stayInQueueStatus) STAY_IN_QUEUE else PLAYING_TRACK
    }

    private fun applyEmbedProperties(
        embedCreateSpec: LegacyEmbedCreateSpec,
        title: String?,
        description: String?,
        thumbnail: String?,
        footer: String?,
        color: Color?,
        timestamp: Instant?,
        image: String?,
        author: String?
    ) {
        title?.let { embedCreateSpec.setTitle(it) }
        description?.let { embedCreateSpec.setDescription(it) }
        thumbnail?.let { embedCreateSpec.setThumbnail(it) }
        footer?.let { embedCreateSpec.setFooter(it, null) }
        color?.let { embedCreateSpec.setColor(it) }
        timestamp?.let { embedCreateSpec.setTimestamp(it) }
        image?.let { embedCreateSpec.setImage(it) }
        author?.let { embedCreateSpec.setAuthor(it, null, null) }
    }

    companion object {
        private const val STAY_IN_QUEUE = "Поставлено в очередь"
        private const val PLAYING_TRACK = "Играющий трек"
    }
}