package bot

import com.google.gson.JsonParser
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
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.Integer.min
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit


class Bot(id: String, private val apiKeyYouTube: String) {
    private val playerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        configuration.frameBufferFactory = AudioFrameBufferFactory { bufferDuration, format, stopping ->
            NonAllocatingAudioFrameBuffer(bufferDuration, format, stopping)
        }
        AudioSourceManagers.registerRemoteSources(this)
    }

    private lateinit var clientGeneral: GatewayDiscordClient

    private val musicManagers: MutableMap<Snowflake, GuildMusicManager> = mutableMapOf()

    private val commands: MutableMap<String, Command> = HashMap()

    private val godModeUserId = "284039904495927297"

    init {
        initCommands()
        val client: GatewayDiscordClient? = DiscordClientBuilder.create(id).build().login().block()

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
                return event?.let { play(it) }
            }

        }

        commands["серега"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let {
                    play(it, SEREGA_PIRAT).then(
                        sendMessage(
                            event,
                            "https://tenor.com/view/pirat-serega-pirat-papich-dance-dancing-gif-17296890"
                        )
                    )
                }
            }
        }

        commands["папочка здесь"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let { Mono.fromCallable { godMode(it, true) }.then() }
            }
        }

        commands["godmodeoff"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let { Mono.fromCallable { godMode(it, false) }.then() }
            }
        }

        commands["help"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                TODO("Not yet implemented")
            }

        }

        commands["stop"] = object : Command {
            override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                return event?.let { stopPlaying(it) }
            }
        }

        commands["next"] =
            object : Command {
                override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                    val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                    val musicManager = getGuildMusicManager(guildId)
                    musicManager.scheduler.loop = false

                    return sendMessage(event, "Включен следующий трек").let {
                        Mono.fromCallable { musicManager.scheduler.nextTrack() }.then(it)
                    }
                }
            }

        commands["queue"] =
            object : Command {
                override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                    return event?.let { showTrackList(it).then() }
                }
            }

        commands["what"] =
            object : Command {
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

        commands["loop"] =
            object : Command {
                override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                    val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                    val musicManager = getGuildMusicManager(guildId)
                    musicManager.scheduler.loop = !musicManager.scheduler.loop

                    return if (musicManager.scheduler.currentTrack != null) {
                        Mono.fromCallable {}.flatMap {
                            if (musicManager.scheduler.loop) sendMessage(event, "Повтор включен")
                            else sendMessage(event, "Повтор выключен")
                        }.flatMap {
                            sendEmbedMessage(
                                event,
                                musicManager.scheduler.currentTrack!!,
                                musicManager.scheduler.loop,
                                loopPlaylist = false,
                                stayInQueueStatus = false
                            )
                        }
                    } else {
                        Mono.empty()
                    }
                }
            }

        commands["shuffle"] =
            object : Command {
                override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                    val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                    val musicManager = getGuildMusicManager(guildId)

                    return sendMessage(event, "Очередь перемешана").let {
                        Mono.fromCallable { musicManager.scheduler.shuffleQueue() }.then(it)
                    }
                }
            }

        commands["playlistloop"] =
            object : Command {
                override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                    val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
                    val musicManager = getGuildMusicManager(guildId)

                    return Mono.fromCallable {
                        musicManager.scheduler.playlistLoop = !musicManager.scheduler.playlistLoop
                    }.flatMap {
                        if (musicManager.scheduler.playlistLoop) sendMessage(
                            event,
                            "Циклический повтор плейлиста включен"
                        )
                        else sendMessage(event, "Циклический повтор плейлиста выключен")
                    }
                }
            }

        commands["jump"] =
            object : Command {
                override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
                    return event?.let { jump(it) }
                }
            }
    }

    fun godMode(event: MessageCreateEvent, status: Boolean) {
        val guildId = event.guildId.orElse(null)
        val musicManager = getGuildMusicManager(guildId)

        val senderId = event.message.author.orElse(null)?.id?.asString()
        if (senderId == godModeUserId) {
            musicManager.godMode = status
            if (status)
                sendMessage(event, "godmode enable").then(
                    sendMessage(
                        event,
                        "https://media.discordapp.net/attachments/965181691981357056/1009410729406906389/2cabf388b2232cc6b21d42cfb5d30266.gif"
                    )
                ).subscribe()
            else
                sendMessage(event, "godmode disabled").subscribe()
        } else {
            event.message.channel.flatMap {
                it.createMessage("https://tenor.com/view/%D0%BF%D0%BE%D1%88%C3%AB%D0%BB%D0%BD%D0%B0%D1%85%D1%83%D0%B9-gif-22853707")
            }.subscribe()
        }
    }


    fun play(event: MessageCreateEvent): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
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
                                if (command.size > 1) {
                                    val input = command.subList(1, command.size).joinToString(" ")
                                    if (!input.matches(Regex("^(https?|ftp)://[^\\s/$.?#].\\S*$"))) {
                                        val youtubeSearchResult = searchYoutube(input)
                                        if (youtubeSearchResult != null) {
                                            playerManager.loadItem(youtubeSearchResult, musicManager.scheduler)
                                        }
                                    } else {
                                        playerManager.loadItem(input, musicManager.scheduler)
                                    }
                                    musicManager.player.addListener(musicManager.scheduler)
                                }
                            }
                    )
            }
            .then()
    }


    private fun searchYoutube(query: String): String? {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val apiUrl =
            "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=1&q=$encodedQuery&key=$apiKeyYouTube"

        return try {
            val resultJson = URL(apiUrl).readText()
            val jsonObject = JsonParser.parseString(resultJson).asJsonObject
            val items = jsonObject.getAsJsonArray("items")
            if (items.size() > 0) {
                val videoId = items.get(0).asJsonObject.getAsJsonObject("id").get("videoId").asString
                "https://www.youtube.com/watch?v=$videoId"
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    fun jump(event: MessageCreateEvent): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        return run {
            val args = event.message.content.split(" ")
            if (args.size > 1) {
                val index = args[1].toIntOrNull()
                if (index != null && index > 1 && index <= musicManager.scheduler.getFullTrackList().size) {
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

    fun play(event: MessageCreateEvent, link: String): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty<Void>()
        val musicManager = getGuildMusicManager(guildId)

        musicManager.scheduler.currentEvent = event

        return Mono.justOrEmpty(event.member)
            .flatMap { it.voiceState }
            .flatMap { it.channel }
            .flatMap { channel ->
                channel.join { spec -> spec.setProvider(musicManager.provider) }
                    .then(
                        Mono.fromRunnable<Void> {
                            try {
                                playerManager.loadItem(link, musicManager.scheduler)
                                musicManager.player.addListener(musicManager.scheduler)
                            } catch (e: Exception) {
                                println("An error occurred: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    )
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
        return event.message.channel.flatMap { channel -> channel.createMessage(message) }.then()
    }

    private fun stopPlaying(event: MessageCreateEvent): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        return Mono.justOrEmpty(event.member.orElse(null)).flatMap { member ->
            Mono.justOrEmpty(member?.voiceState?.block()).flatMap { voiceState ->
                Mono.justOrEmpty(voiceState?.channel?.block()).doOnNext { channel ->
                    channel?.sendDisconnectVoiceState()?.block()
                        .let { musicManager.scheduler.clearQueue() }
                }
            }
        }.then(
            sendMessage(event, "Воспроизведение остановлено")
        ).then(
            sendMessage(
                event,
                "https://media.discordapp.net/attachments/816984360665219113/959247739575750717/bylling.gif"
            )
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

        return event.message.channel.flatMap { channel ->
            channel.createEmbed { embedCreateSpec ->
                embedCreateSpec.setTitle("Список песен (Страница ${currentPage + 1} из $totalPages)")
                embedCreateSpec.setDescription(formatTrackListPage(currentPage))
            }.flatMap { message ->
                if (totalPages > 1) {
                    message.addReaction(ReactionEmoji.unicode("⬅"))
                        .then(message.addReaction(ReactionEmoji.unicode("➡")))
                        .then(message.client.eventDispatcher.on(ReactionAddEvent::class.java)
                            .filter { it.messageId == message.id }
                            .filter { it.userId == event.message.author.get().id }
                            .filter { it.emoji.asUnicodeEmoji().isPresent }.take(totalPages.toLong())
                            .flatMap { reactionEvent ->
                                val changePage = when (reactionEvent.emoji.asUnicodeEmoji().get()) {
                                    ReactionEmoji.unicode("⬅") -> {
                                        if (currentPage > 0) {
                                            currentPage--
                                            true
                                        } else false
                                    }

                                    ReactionEmoji.unicode("➡") -> {
                                        if (currentPage < totalPages - 1) {
                                            currentPage++
                                            true
                                        } else false
                                    }

                                    else -> false
                                }

                                if (changePage) {
                                    message.edit { spec ->
                                        spec.setEmbed { embedCreateSpec ->
                                            embedCreateSpec.setTitle("Список песен (Страница ${currentPage + 1} из $totalPages)")
                                            embedCreateSpec.setDescription(formatTrackListPage(currentPage))
                                        }
                                    }.then()
                                } else {
                                    Mono.empty<Void>()
                                }
                            }.then()
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
        val guildId = event.guildId.orElse(null)
        val musicManager = getGuildMusicManager(guildId)

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


    private fun setEventObserver(client: GatewayDiscordClient) {
        observeMessageEvents(client)
        observeVoiceEvents(client)
    }

    private fun observeMessageEvents(client: GatewayDiscordClient) {
        client.eventDispatcher.on(MessageCreateEvent::class.java).flatMap { event ->
            val guildId = event?.guildId?.orElse(null) ?: return@flatMap Mono.empty()
            val musicManager = getGuildMusicManager(guildId)

            Mono.just(event.message.content).flatMap { content ->
                if (musicManager.godMode && event.message.author.orElse(null)?.id?.asString() != godModeUserId) {
                    Mono.empty()
                } else {
                    Flux.fromIterable(commands.entries)
                        .filter { entry -> content.startsWith('!' + entry.key, ignoreCase = true) }
                        .flatMap { entry -> entry.value.execute(event) }.next()
                }
            }
        }.subscribe()
    }

    private fun observeVoiceEvents(client: GatewayDiscordClient) {
        client.eventDispatcher.on(VoiceStateUpdateEvent::class.java).flatMap { event ->
            val guildId = event.current.guildId
            val selfId = client.selfId
            val oldChannelId = event.old.orElse(null)?.channelId?.orElse(null)
            val currentChannelId = event.current.channelId.orElse(null)

            if (oldChannelId == null && currentChannelId == null) {
                println("No channel state change")
                return@flatMap Mono.empty<Void>()
            }

            println("Old channel ID: $oldChannelId, Current channel ID: $currentChannelId")

            Mono.defer {
                oldChannelId?.let { oldId ->
                    println("Old channel")
                    client.getChannelById(oldId).ofType(VoiceChannel::class.java).flatMap { channel ->
                        channel.voiceStates.collectList().flatMap { voiceStates ->
                            handleVoiceState(selfId, guildId, channel, voiceStates)
                        }
                    }.onErrorResume {
                        println("Error: $it")
                        Mono.empty()
                    }
                } ?: Mono.empty<Void>()
            }.then(Mono.defer {
                currentChannelId?.let { currentId ->
                    println("Current channel")
                    client.getChannelById(currentId).ofType(VoiceChannel::class.java).flatMap { channel ->
                        channel.voiceStates.collectList().flatMap { voiceStates ->
                            handleVoiceState(selfId, guildId, channel, voiceStates)
                        }
                    }.onErrorResume { Mono.empty<Void>() }
                } ?: Mono.empty<Void>()
            })
        }.doOnError { error ->
            println("An error occurred: ${error.message}")
            error.printStackTrace()
        }.subscribe()
    }


    private fun handleVoiceState(
        selfId: Snowflake, guildId: Snowflake, channel: VoiceChannel, voiceStates: List<VoiceState>
    ): Mono<Void> {
        return if (voiceStates.size == 1 && voiceStates[0].userId == selfId) {
            println("Only bot is present in the voice channel. Disconnecting and clearing the queue.")
            channel.sendDisconnectVoiceState().then(Mono.fromRunnable<Void?> {
                getGuildMusicManager(guildId).scheduler.clearQueue()
            }.flatMap {
                getGuildMusicManager(guildId).scheduler.currentEvent?.let { it1 ->
                    sendMessage(
                        it1, "Воспроизведение остановлено"
                    )
                }
            })
        } else {
            println("More than one member is present in the voice channel.")
            Mono.empty()
        }
    }

    companion object {
        const val SEREGA_PIRAT = "https://www.youtube.com/watch?v=KhX3T_NYndo&list=PLaxxU3ZabospOFUVjRWofD-mYOQfCxpzw"
        const val VI_KA = "https://www.youtube.com/watch?v=SXe1aTEJU2w&pp=ygUFVkkgS0E%3D"
    }
}