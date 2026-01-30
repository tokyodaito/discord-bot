package service.music

import bot.Bot
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import manager.GuildManager.getGuildMusicManager
import manager.GuildManager.playerManager
import manager.GuildMusicManager
import music.TrackScheduler
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.lang.Integer.min
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class MusicService {
    private val youTubeImpl = Bot.remoteComponent.getYouTubeImpl()
    private val messageService = Bot.serviceComponent.getMessageService()
    private val voiceChannelService = Bot.serviceComponent.getVoiceChannelService()
    private val favorites = Favorites()

    fun play(event: MessageCreateEvent, link: String?): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)
        musicManager.scheduler.currentEvent = event

        return voiceChannelService.checkBotInVoiceChannel(event).flatMap { botInServer ->
            if (botInServer) {
                processBotInServer(event, musicManager, link)
            } else {
                processBotNotInServer(event, musicManager, link)
            }
        }.onErrorResume {
            println("Error play: $it")
            Mono.empty()
        }
    }

    fun play(event: MessageCreateEvent): Mono<Void?> {
        return play(event, null)
    }

    fun jump(event: MessageCreateEvent): Mono<Void?> {
        val musicManager = getGuildMusicManager(event) ?: return Mono.empty()
        val args = event.message.content.split(" ")

        return when {
            args.size > 1 -> handleJumpWithArgs(event, musicManager, args)
            else -> sendMessage(event, TRACK_INDEX_NOTIFICATION).then()
        }
    }

    private fun handleJumpWithArgs(
        event: MessageCreateEvent,
        musicManager: GuildMusicManager,
        args: List<String>
    ): Mono<Void?> {
        val index = args[1].toIntOrNull()
        return if (index != null && musicManager.scheduler.jumpToTrack(index)) {
            handleSuccessfulJump(event, musicManager, index)
        } else {
            sendMessage(event, INVALID_INDEX).then()
        }
    }

    private fun handleSuccessfulJump(
        event: MessageCreateEvent,
        musicManager: GuildMusicManager,
        index: Int
    ): Mono<Void?> {
        val track = musicManager.scheduler.getFullTrackList()[index - 1]
        return sendMessage(event, "$TRACK_SWITCH_NOTIFICATION $index").then(
            messageService.sendInformationAboutSong(
                event, track, musicManager.scheduler.loop,
                musicManager.scheduler.playlistLoop, false
            )
        )
    }

    fun stopPlaying(event: MessageCreateEvent): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        return voiceChannelService.checkUserInVoiceChannelWithBot(event)
            .flatMap { isInSameChannel -> handleVoiceChannelCheckResult(isInSameChannel, event, musicManager) }
            .onErrorResume {
                println("Error stopPlaying: $it")
                Mono.empty()
            }
    }

    private fun handleVoiceChannelCheckResult(
        isInSameChannel: Boolean, event: MessageCreateEvent, musicManager: GuildMusicManager
    ): Mono<Void?> {
        return if (!isInSameChannel) {
            sendMessage(event, NOT_IN_VOICE_CHANNEL)
        } else {
            stopMusicAndSendMessages(event, musicManager)
        }
    }

    private fun stopMusicAndSendMessages(
        event: MessageCreateEvent, musicManager: GuildMusicManager
    ): Mono<Void?> {
        return stopMusic(event, musicManager).then(
            sendMessage(event, PLAYBACK_STOPPED)
        )
    }

    fun showTrackList(event: MessageCreateEvent): Mono<Void> {
        val musicManager = getGuildMusicManager(event) ?: return Mono.empty()
        val authorId = event.message.author.map { it.id }.orElse(null) ?: return Mono.empty()
        val tracks = musicManager.scheduler.getFullTrackList()
        val tracksPerPage = 10
        val totalPages = (tracks.size + tracksPerPage - 1) / tracksPerPage
        val currentPage = AtomicInteger(0)

        fun formatTrackListPage(page: Int): String {
            val startIndex = page * tracksPerPage
            val endIndex = min(startIndex + tracksPerPage, tracks.size)
            return tracks.subList(startIndex, endIndex).mapIndexed { index, track ->
                "${startIndex + index + 1}. ${track.info.title}"
            }.joinToString("\n")
        }

        fun sendInitialEmbed(): Mono<Message> {
            return messageService.createEmbedMessage(
                event,
                title = "$SONGS_LIST (Страница ${currentPage.get() + 1} из $totalPages)",
                description = formatTrackListPage(currentPage.get())
            )
        }

        fun addReactionsToMessage(message: Message): Mono<Void> {
            if (totalPages <= 1) {
                return Mono.empty()
            }

            return message.addReaction(ReactionEmoji.unicode("⬅"))
                .then(message.addReaction(ReactionEmoji.unicode("➡")))
                .then()
        }

        fun processReactionEvents(message: Message): Mono<Void> {
            return message.client.eventDispatcher.on(ReactionAddEvent::class.java)
                .filter { it.messageId == message.id }
                .filter { it.userId == authorId }
                .filter { it.emoji.asUnicodeEmoji().isPresent }
                .take(Duration.ofMinutes(10))
                .flatMap { reactionEvent ->
                    when (reactionEvent.emoji.asUnicodeEmoji().get()) {
                        ReactionEmoji.unicode("⬅") -> {
                            if (currentPage.get() > 0) {
                                currentPage.decrementAndGet()
                            }
                        }
                        ReactionEmoji.unicode("➡") -> {
                            if (currentPage.get() < totalPages - 1) {
                                currentPage.incrementAndGet()
                            }
                        }
                    }
                    messageService.editEmbedMessage(
                        message,
                        title = "$SONGS_LIST (Страница ${currentPage.get() + 1} из $totalPages)",
                        description = formatTrackListPage(currentPage.get())
                    )
                }
                .then()
        }

        return sendInitialEmbed()
            .flatMap {
                addReactionsToMessage(it).then(
                    processReactionEvents(it)
                )
            }
            .then()
    }


    fun nextTrack(event: MessageCreateEvent): Boolean {
        val musicManager = getGuildMusicManager(event) ?: return false

        return if (musicManager.scheduler.getFullTrackList().size <= 1) {
            false
        } else {
            musicManager.scheduler.loop = false
            musicManager.scheduler.nextTrack()
            true
        }
    }

    fun deleteTrack(event: MessageCreateEvent): Mono<Void?> {
        val musicManager = getGuildMusicManager(event) ?: return Mono.empty()

        return run {
            val args = event.message.content.split(" ")
            if (args.size > 1) {
                val index = args[1].toIntOrNull()
                if (index != null && musicManager.scheduler.deleteTrack(index)) {
                    sendMessage(event, TRACK_REMOVED)
                } else {
                    sendMessage(event, INVALID_INDEX)
                }
            } else {
                sendMessage(event, INVALID_INDEX)
            }
        }
    }

    fun saveFavorite(event: MessageCreateEvent): Mono<Void?> {
        return favorites.saveFavorite(event)
    }

    fun getFavorites(event: MessageCreateEvent): Mono<Void?> {
        return favorites.getFavoritesToDisplay(event)
    }

    fun playFavorite(event: MessageCreateEvent): Mono<Void?> {
        return favorites.getLinkOfFavorite(event).flatMap {
            if (it != null) {
                play(event, it)
            } else {
                Mono.empty()
            }
        }.onErrorResume {
            println("Error playFavorite: $it")
            Mono.empty()
        }
    }

    fun removeFromFavorite(event: MessageCreateEvent): Mono<Void?> {
        return favorites.removeFromFavorite(event).then()
    }

    fun addToFavoritesCurrentTrack(event: MessageCreateEvent): Mono<Void?> {
        return favorites.addTheCurrentTrackToFavorites(event).then()
    }

    private fun processBotInServer(
        event: MessageCreateEvent, musicManager: GuildMusicManager, link: String?
    ): Mono<Void?> {
        return voiceChannelService.checkUserInVoiceChannelWithBot(event).flatMap { userInVoice ->
            if (!userInVoice) {
                return@flatMap sendMessage(event, NOT_IN_VOICE_CHANNEL)
            }
            if (link != null) {
                musicManager.attachSchedulerListener()
                loadMusic(link, musicManager.scheduler)
            } else {
                musicManager.attachSchedulerListener()
                processYoutubeLink(event, musicManager.scheduler)
            }
        }
    }

    private fun processBotNotInServer(
        event: MessageCreateEvent, musicManager: GuildMusicManager, link: String?
    ): Mono<Void?> {
        return voiceChannelService.checkUserInVoiceChannel(event).flatMap { userInVoice ->
            if (!userInVoice) {
                return@flatMap sendMessage(event, NOT_IN_VOICE_CHANNEL)
            }
            voiceChannelService.join(event).then(
                if (link != null) {
                    musicManager.attachSchedulerListener()
                    loadMusic(link, musicManager.scheduler)
                } else {
                    musicManager.attachSchedulerListener()
                    processYoutubeLink(event, musicManager.scheduler)
                }
            )
        }
    }

    private fun processYoutubeLink(event: MessageCreateEvent, scheduler: TrackScheduler): Mono<Void?> {
        val content = event.message.content.orEmpty()
        if (content.isBlank()) return Mono.empty()

        val command = content.split(" ")
        if (command.size <= 1) return Mono.empty()

        val input = command.subList(1, command.size).joinToString(" ")
        return if (isValidYoutubeLink(input)) {
            loadItem(normalizeUrl(input), scheduler)
        } else {
            searchAndLoadItem(input, scheduler)
        }
    }

    private fun isValidYoutubeLink(input: String): Boolean {
        return looksLikeUrl(input)
    }

    private fun looksLikeUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.any { it.isWhitespace() }) return false

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return true
        if (trimmed.startsWith("www.")) return true

        val hasDomain = trimmed.contains('.')
        val hasPathOrQuery = trimmed.contains('/') || trimmed.contains('?') || trimmed.contains('=')
        return hasDomain && hasPathOrQuery
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun loadItem(input: String, scheduler: TrackScheduler): Mono<Void?> {
        return Mono.fromCallable {
            runCatching { playerManager.loadItem(input, scheduler) }
                .onFailure { e -> println("Error loadItem: $e") }
            true
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    private fun searchAndLoadItem(input: String, scheduler: TrackScheduler): Mono<Void?> {
        return Mono.fromCallable { youTubeImpl.searchYoutube(input) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { url -> if (url != null) loadItem(url, scheduler) else Mono.empty() }
            .then()
    }


    private fun loadMusic(link: String, scheduler: TrackScheduler): Mono<Void?> {
        return Mono.fromRunnable<Void> {
            try {
                playerManager.loadItem(link, scheduler)
            } catch (e: Exception) {
                println("An error occurred: ${e.message}")
                e.printStackTrace()
            }
        }.subscribeOn(Schedulers.boundedElastic()).then()
    }

    private fun stopMusic(event: MessageCreateEvent, musicManager: GuildMusicManager): Mono<VoiceChannel> {
        return Mono.justOrEmpty(event.member.orElse(null))
            .flatMap { member ->
                member.voiceState.flatMap { voiceState ->
                    voiceState.channel
                }
            }
            .flatMap { channel ->
                voiceChannelService.disconnect(event)
                    .doOnSuccess {
                        musicManager.detachSchedulerListener()
                        musicManager.scheduler.clearQueue()
                    }
                    .thenReturn(channel)
            }
    }

    private fun sendMessage(event: MessageCreateEvent, message: String): Mono<Void?> {
        return messageService.createEmbedMessage(event, message).then()
    }

    companion object {
        const val TRACK_INDEX_NOTIFICATION = "🔢 Укажите индекс трека"
        const val TRACK_SWITCH_NOTIFICATION = "⏭ Перешёл к треку с индексом "
        const val NOT_IN_VOICE_CHANNEL = "🚫 Вы не находитесь в голосовом канале"
        const val PLAYBACK_STOPPED = "⏹ Воспроизведение остановлено"
        const val SONGS_LIST = "📜 Список песен"
        const val INVALID_INDEX = "❌ Неверный индекс"
        const val TRACK_REMOVED = "🗑 Трек удален"
    }
}
