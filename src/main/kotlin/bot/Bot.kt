package bot

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBufferFactory
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import discord4j.common.util.Snowflake
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.`object`.reaction.ReactionEmoji
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

    private lateinit var clientGeneral: GatewayDiscordClient

    private val musicManagers: MutableMap<Snowflake, GuildMusicManager> = mutableMapOf()

    private val commands: MutableMap<String, Command> = HashMap()

    init {
        initCommands()
        val client: GatewayDiscordClient? = DiscordClientBuilder.create(id).build()
            .login()
            .block()

        if (client != null) {
            setEventObserver(client)
            clientGeneral = client
            println("Bot init!")
        }

        client?.onDisconnect()?.block()
    }

    private fun initCommands() {
        commands["ping"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let { sendMessage(it, "Pong!") }
            }
        }

        commands["play"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                val musicManager = getGuildMusicManager(guildId)

                musicManager.scheduler.currentEvent = event

                return Mono.justOrEmpty(event.member)
                    .flatMap { it.voiceState }
                    .flatMap { it.channel }
                    .flatMap { channel ->
                        channel.join { spec -> spec.setProvider(musicManager.provider) }
                            .then(
                                Mono.justOrEmpty(event.message.content)
                                    .map { content -> content.split(" ") }
                                    .doOnNext { command ->
                                        if (command != null) {
                                            try {
                                                playerManager.loadItem(command[1], musicManager.scheduler)
                                                musicManager.player.addListener(musicManager.scheduler)
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
                return event?.let { stopPlaying(it) }
            }
        }

        commands["next"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                val musicManager = getGuildMusicManager(guildId)

                return sendMessage(event, "Включен следующий трек").let {
                    Mono.fromCallable { musicManager.scheduler.nextTrack() }.then(it)
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
                val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                val musicManager = getGuildMusicManager(guildId)

                return musicManager.scheduler.currentTrack?.let { track ->
                    sendEmbedMessage(
                        event,
                        track,
                        loop = false,
                        loopPlaylist = false,
                        stayInQueueStatus = musicManager.scheduler.loop
                    ).then()
                }
            }
        }

        commands["loop"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                val musicManager = getGuildMusicManager(guildId)

                return if (musicManager.scheduler.currentTrack != null) {
                    Mono.fromCallable { musicManager.scheduler.loop = !musicManager.scheduler.loop }
                        .flatMap {
                            if (musicManager.scheduler.loop)
                                sendMessage(event, "Повтор включен")
                            else
                                sendMessage(event, "Повтор выключен")
                        }
                        .then(
                            sendEmbedMessage(
                                event, musicManager.scheduler.currentTrack!!, musicManager.scheduler.loop,
                                loopPlaylist = false, stayInQueueStatus = false
                            )
                        )
                } else {
                    Mono.empty()
                }
            }
        }

        commands["shuffle"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                val musicManager = getGuildMusicManager(guildId)

                return sendMessage(event, "Очередь перемешана").let {
                    Mono.fromCallable { musicManager.scheduler.shuffleQueue() }.then(it)
                }
            }
        }

        commands["playlistloop"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                val musicManager = getGuildMusicManager(guildId)

                return Mono.fromCallable { musicManager.scheduler.playlistLoop = !musicManager.scheduler.playlistLoop }
                    .flatMap {
                        if (musicManager.scheduler.playlistLoop)
                            sendMessage(event, "Циклический повтор плейлиста включен")
                        else
                            sendMessage(event, "Циклический повтор плейлиста выключен")
                    }
            }
        }

        commands["jump"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                val musicManager = getGuildMusicManager(guildId)

                return run {
                    val args = event.message.content.split(" ")
                    if (args.size > 1) {
                        val index = args[1].toIntOrNull()
                        if (index != null && index > 0 && index <= musicManager.scheduler.getFullTrackList().size) {
                            val track = musicManager.scheduler.getFullTrackList()[index - 1]
                            musicManager.scheduler.currentTrack = track
                            musicManager.scheduler.player.startTrack(track, false)
                            sendMessage(event, "Перешёл к треку с индексом $index").then(
                                sendEmbedMessage(
                                    event,
                                    track,
                                    musicManager.scheduler.loop,
                                    musicManager.scheduler.playlistLoop,
                                    false
                                )
                            )
                        } else {
                            sendMessage(event, "Неверный индекс")
                        }
                    } else {
                        sendMessage(event, "Укажите индекс трека")
                    }
                }
            }
        }
    }

    fun getGuildMusicManager(guildId: Snowflake): GuildMusicManager {
        return musicManagers.computeIfAbsent(guildId) {
            GuildMusicManager(playerManager) { messageCreateEvent, audioTrack, loop, loopPlaylist, stayInQueue ->
                sendEmbedMessage(messageCreateEvent, audioTrack, loop, loopPlaylist, stayInQueue)
            }
        }
    }


    private fun sendMessage(event: MessageCreateEvent, message: String): Mono<Void?> {
        return event.message.channel
            .flatMap { channel -> channel.createMessage(message) }
            .then()
    }

    private fun stopPlaying(event: MessageCreateEvent): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        return Mono.justOrEmpty(event.member.orElse(null))
            .flatMap { member ->
                Mono.justOrEmpty(member?.voiceState?.block())
                    .flatMap { voiceState ->
                        Mono.justOrEmpty(voiceState?.channel?.block())
                            .doOnNext { channel ->
                                channel?.sendDisconnectVoiceState()?.block().let { musicManager.scheduler.clearQueue() }
                            }
                    }
            }
            .then(
                sendMessage(event, "Воспроизведение остановлено")
            )
    }

    private fun showTrackList(event: MessageCreateEvent): Mono<Void> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        val tracks = musicManager.scheduler.getFullTrackList()
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
                        Mono.empty()
                    }
                }
            }
    }


    private fun sendEmbedMessage(
        event: MessageCreateEvent,
        track: AudioTrack,
        loop: Boolean,
        loopPlaylist: Boolean,
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
                    val loopPlaylistStatus = if (loopPlaylist) "Повтор плейлиста включен" else ""
                    embedCreateSpec.setFooter(
                        "Трек длиной: $minutes минут $seconds секунд \n $loopStatus \n $loopPlaylistStatus",
                        null
                    )
                }
            }
            .then()
    }


    private fun setEventObserver(client: GatewayDiscordClient) {
        observeMessageEvents(client)
        observeVoiceEvents(client)
    }

    private fun observeMessageEvents(client: GatewayDiscordClient) {
        client.eventDispatcher.on(MessageCreateEvent::class.java)
            .flatMap { event ->
                Mono.just(event.message.content)
                    .flatMap { content ->
                        Flux.fromIterable(commands.entries)
                            .filter { entry -> content.startsWith('!' + entry.key, ignoreCase = true) }
                            .flatMap { entry -> entry.value.execute(event) }
                            .next()
                    }
            }
            .subscribe()
    }

    private fun observeVoiceEvents(client: GatewayDiscordClient) {
        client.eventDispatcher.on(VoiceStateUpdateEvent::class.java)
            .flatMap { event ->
                val guildId = event.current.guildId
                val selfId = client.selfId
                val oldChannelId = event.old.orElse(null)?.channelId?.orElse(null)
                val currentChannelId = event.current.channelId.orElse(null)

                if (oldChannelId == null && currentChannelId == null) {
                    println("No channel state change")
                    return@flatMap Mono.empty<Void>()
                }

                println("Old channel ID: $oldChannelId, Current channel ID: $currentChannelId")

                event.current.channel.flatMap { channel ->
                    channel.voiceStates.collectList().flatMap { voiceStates ->
                        if (voiceStates.size == 1 && voiceStates[0].userId == selfId) {
                            println("Only bot is present in the voice channel. Disconnecting and clearing the queue.")
                            channel.sendDisconnectVoiceState().then(Mono.fromRunnable {
                                getGuildMusicManager(guildId).scheduler.clearQueue()
                            })
                        } else {
                            println("More than one member is present in the voice channel.")
                            Mono.empty()
                        }
                    }
                }
            }
            .doOnError { error ->
                println("An error occurred: ${error.message}")
                error.printStackTrace()
            }
            .subscribe()
    }
}