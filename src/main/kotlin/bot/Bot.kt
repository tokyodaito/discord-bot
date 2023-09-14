package bot

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBufferFactory
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.`object`.reaction.ReactionEmoji
import discord4j.voice.AudioProvider
import music.LavaPlayerAudioProvider
import music.TrackScheduler
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.Integer.min
import java.util.concurrent.TimeUnit


class Bot(id: String) {
    private val playerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        configuration
            .frameBufferFactory = AudioFrameBufferFactory { bufferDuration, format, stopping ->
            NonAllocatingAudioFrameBuffer(bufferDuration, format, stopping)
        }
        AudioSourceManagers.registerRemoteSources(this)
    }

    private val player: AudioPlayer = playerManager.createPlayer()
    var provider: AudioProvider = LavaPlayerAudioProvider(player)

    private val commands: MutableMap<String, Command> = HashMap()
    private val scheduler = TrackScheduler(player) { messageCreateEvent, audioTrack, loop, stayInQueue ->
        sendEmbedMessage(messageCreateEvent, audioTrack, loop, stayInQueue)
    }

    init {
        initCommands()
        val client: GatewayDiscordClient? = DiscordClientBuilder.create(id).build()
            .login()
            .block()

        if (client != null) {
            setEventObserver(client)
            println("Bot init!")
        }

        client?.onDisconnect()?.block();
    }

    private fun initCommands() {
        commands["ping"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let { sendMessage(it, "Pong!") }
            }
        }

        commands["play"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                scheduler.currentEvent = event
                return Mono.justOrEmpty(event?.member)
                    .flatMap { it.voiceState }
                    .flatMap { it.channel }
                    .flatMap { channel ->
                        channel.join { spec -> spec.setProvider(provider) }
                            .then(
                                Mono.justOrEmpty(event?.message?.content)
                                    .map { content -> content?.split(" ") }
                                    .doOnNext { command ->
                                        if (command != null) {
                                            try {
                                                playerManager.loadItem(command[1], scheduler)
                                                player.addListener(scheduler)
                                            } catch (e: Exception) {
                                                println("An error occurred: ${e.message}")
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                            )
                    }
                    .then()
            }
        }

        commands["stop"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let { sendMessage(it, "Воспроизведение остановлено") }?.let {
                    Mono.justOrEmpty(event.member.orElse(null))
                        .flatMap { member ->
                            Mono.justOrEmpty(member?.voiceState?.block())
                                .flatMap { voiceState ->
                                    Mono.justOrEmpty(voiceState?.channel?.block())
                                        .doOnNext { channel ->
                                            channel?.sendDisconnectVoiceState()?.block().let { scheduler.clearQueue() }
                                        }
                                }
                        }
                        .then(
                            it
                        )
                }
            }
        }

        commands["next"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let { sendMessage(it, "Включен следующий трек") }?.let {
                    Mono.fromCallable { scheduler.nextTrack() }.then(
                        it
                    )
                }
            }
        }

        commands["queue"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let { showTrackList(it).then() }
            }
        }

        commands["what"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let {
                    scheduler.currentTrack?.let { it1 ->
                        sendEmbedMessage(
                            it,
                            it1,
                            scheduler.loop,
                            false
                        ).then()
                    }
                }
            }
        }

        commands["loop"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return if (scheduler.currentTrack != null && event != null) {
                    Mono.fromCallable { scheduler.loop = !scheduler.loop }.then(
                        if (scheduler.loop)
                            sendMessage(event, "Повтор включен")
                        else
                            sendMessage(event, "Повтор выключен")
                    ).then(
                        sendEmbedMessage(event, scheduler.currentTrack!!, scheduler.loop, false)
                    )
                } else {
                    Mono.empty()
                }
            }
        }


        commands["shuffle"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let { sendMessage(it, "Очередь перемешана") }?.let {
                    Mono.fromCallable { scheduler.shuffleQueue() }.then(
                        it
                    )
                }
            }
        }
    }

    private fun sendMessage(event: MessageCreateEvent, message: String): Mono<Void?> {
        return event.message.channel
            .flatMap { channel -> channel.createMessage(message) }
            .then()
    }

    private fun showTrackList(event: MessageCreateEvent): Mono<Void> {
        val tracks = scheduler.getFullTrackList()
        val tracksPerPage = 10
        val totalPages = (tracks.size + tracksPerPage - 1) / tracksPerPage
        var currentPage = 0

        fun formatTrackListPage(page: Int): String {
            val startIndex = page * tracksPerPage
            val endIndex = min(startIndex + tracksPerPage, tracks.size)
            return tracks.subList(startIndex, endIndex).mapIndexed { index, track ->
                "${startIndex + index + 1}. ${track.info.title}"
            }.joinToString("\n")
        }

        return event.message.channel
            .flatMap { channel ->
                channel.createEmbed { embedCreateSpec ->
                    embedCreateSpec.setTitle("Список песен (Страница ${currentPage + 1} из $totalPages)")
                    embedCreateSpec.setDescription(formatTrackListPage(currentPage))
                }.flatMap { message ->
                    if (totalPages > 1) {
                        message.addReaction(ReactionEmoji.unicode("⬅"))
                            .then(message.addReaction(ReactionEmoji.unicode("➡")))
                            .then(
                                message.client.eventDispatcher.on(ReactionAddEvent::class.java)
                                    .filter { it.messageId == message.id }
                                    .filter { it.userId == event.message.author.get().id }
                                    .filter { it.emoji.asUnicodeEmoji().isPresent }
                                    .take(totalPages.toLong())
                                    .flatMap { reactionEvent ->
                                        when (reactionEvent.emoji.asUnicodeEmoji().get()) {
                                            ReactionEmoji.unicode("⬅") -> {
                                                if (currentPage > 0) {
                                                    currentPage--
                                                }
                                            }

                                            ReactionEmoji.unicode("➡") -> {
                                                if (currentPage < totalPages - 1) {
                                                    currentPage++
                                                }
                                            }
                                        }
                                        message.edit { spec ->
                                            spec.setEmbed { embedCreateSpec ->
                                                embedCreateSpec.setTitle("Список песен (Страница ${currentPage + 1} из $totalPages)")
                                                embedCreateSpec.setDescription(formatTrackListPage(currentPage))
                                            }
                                        }.then(Mono.empty<Void>())
                                    }
                                    .then()
                            )
                    } else {
                        Mono.empty<Void>()
                    }
                }
            }
    }


    private fun sendEmbedMessage(
        event: MessageCreateEvent,
        track: AudioTrack,
        loop: Boolean,
        stayInQueueStatus: Boolean
    ): Mono<Void> {
        return event.message.channel
            .flatMap { channel ->
                channel.createEmbed { embedCreateSpec ->
                    val stayInQueue = if (stayInQueueStatus) "Поставлено в очередь" else "Играющий трек"
                    embedCreateSpec.setTitle(stayInQueue)
                    embedCreateSpec.setDescription("[${track.info.title}](https://www.youtube.com/watch?v=${track.info.identifier}) - ${track.info.author}")
                    embedCreateSpec.setThumbnail(track.info.artworkUrl)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(track.duration)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(track.duration) % 60
                    val loopStatus = if (loop) "Повтор включен" else ""
                    embedCreateSpec.setFooter("Трек длиной: $minutes минут $seconds секунд \n $loopStatus", null)
                }
            }
            .then()
    }


    private fun setEventObserver(client: GatewayDiscordClient) {
        client.eventDispatcher.on(MessageCreateEvent::class.java)
            .flatMap { event ->
                Mono.just(event.message.content)
                    .flatMap { content ->
                        Flux.fromIterable(commands.entries)
                            .filter { entry -> content.startsWith('!' + entry.key) }
                            .flatMap { entry -> entry.value.execute(event) }
                            .next()
                    }
            }
            .subscribe()
    }
}