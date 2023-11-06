package service.music

import bot.Bot
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.core.`object`.reaction.ReactionEmoji
import manager.GuildManager.getGuildMusicManager
import manager.GuildManager.playerManager
import manager.GuildMusicManager
import music.TrackScheduler
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

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
        val musicManager = getGuildMusicManager(event)
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
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)
        val tracks = musicManager.scheduler.getFullTrackList()
        val tracksPerPage = 10
        val totalPages = getTotalPages(tracks, tracksPerPage)
        val currentPage = 0

        return event.message.channel.flatMap { channel ->
            processPage(event, tracks, currentPage, tracksPerPage, totalPages, channel)
        }.onErrorResume {
            println("Error showTrackList: $it")
            Mono.empty()
        }
    }

    private fun processPage(
        event: MessageCreateEvent,
        tracks: List<AudioTrack>,
        currentPage: Int,
        tracksPerPage: Int,
        totalPages: Int,
        channel: MessageChannel
    ): Mono<Void> {
        return formatEmbedMessage(event, tracks, currentPage, tracksPerPage, totalPages).flatMap { message ->
            if (totalPages <= 1) {
                return@flatMap Mono.empty<Void>()
            }
            handlePageReactions(message, totalPages, currentPage, tracks, tracksPerPage, event, channel)
        }
    }

    private fun handlePageReactions(
        message: Message,
        totalPages: Int,
        currentPage: Int,
        tracks: List<AudioTrack>,
        tracksPerPage: Int,
        event: MessageCreateEvent,
        channel: MessageChannel
    ): Mono<Void> {
        return addReactions(message).thenMany(
            handleReactions(
                message,
                totalPages,
                currentPage,
                tracks,
                tracksPerPage,
                event
            )
        ).then()
    }

    private fun getTotalPages(tracks: List<AudioTrack>, tracksPerPage: Int): Int {
        return (tracks.size + tracksPerPage - 1) / tracksPerPage
    }

    private fun formatEmbedMessage(
        event: MessageCreateEvent, tracks: List<AudioTrack>, currentPage: Int, tracksPerPage: Int, totalPages: Int
    ): Mono<Message> {
        return messageService.createEmbedMessage(
            event,
            "$SONGS_LIST (Страница ${currentPage + 1} из $totalPages)",
            formatTrackListPage(tracks, currentPage, tracksPerPage)
        )
    }

    private fun addReactions(message: Message): Mono<Void> {
        return message.addReaction(ReactionEmoji.unicode("⬅")).then(message.addReaction(ReactionEmoji.unicode("➡")))
    }

    private fun handleReactions(
        message: Message,
        totalPages: Int,
        currentPage: Int,
        tracks: List<AudioTrack>,
        tracksPerPage: Int,
        event: MessageCreateEvent
    ): Flux<Void> {
        return message.client.eventDispatcher.on(ReactionAddEvent::class.java).filter { it.messageId == message.id }
            .filter { it.userId == event.message.author.get().id }.filter { it.emoji.asUnicodeEmoji().isPresent }
            .take(totalPages.toLong()).flatMap { reactionEvent ->
                handleReaction(
                    message, reactionEvent, totalPages, currentPage, tracks, tracksPerPage, event
                ).then(Mono.empty<Void>())
            }
    }

    private fun formatTrackListPage(tracks: List<AudioTrack>, page: Int, tracksPerPage: Int): String {
        val startIndex = page * tracksPerPage
        return tracks.drop(startIndex).take(tracksPerPage).mapIndexed { index, track ->
            "${startIndex + index + 1}. ${track.info.title}"
        }.joinToString("\n")
    }

    private fun handleReaction(
        message: Message,
        reactionEvent: ReactionAddEvent,
        totalPages: Int,
        currentPage: Int,
        tracks: List<AudioTrack>,
        tracksPerPage: Int,
        event: MessageCreateEvent
    ): Mono<Void> {
        var page = currentPage
        val changePage = when (reactionEvent.emoji.asUnicodeEmoji().get().raw) {
            "⬅" -> {
                if (page > 0) {
                    page--
                    true
                } else false
            }

            "➡" -> {
                if (page < totalPages - 1) {
                    page++
                    true
                } else false
            }

            else -> false
        }

        return if (changePage) {
            messageService.editEmbedMessage(
                message,
                "$SONGS_LIST (Страница ${page + 1} из $totalPages)",
                formatTrackListPage(tracks, page, tracksPerPage)
            ).thenMany(Flux.fromIterable(listOf("➡", "⬅")).flatMap { emoji ->
                message.removeReaction(
                    ReactionEmoji.unicode(emoji), event.message.author.get().id
                )
            }).then()
        } else {
            Mono.empty()
        }
    }


    fun nextTrack(event: MessageCreateEvent): Boolean {
        val musicManager = getGuildMusicManager(event)

        return if (musicManager.scheduler.getFullTrackList().size <= 1) {
            false
        } else {
            musicManager.scheduler.loop = false
            musicManager.scheduler.nextTrack()
            true
        }
    }

    fun deleteTrack(event: MessageCreateEvent): Mono<Void?> {
        val musicManager = getGuildMusicManager(event)

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

    // TODO
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

    fun removeOfFavorite(event: MessageCreateEvent): Mono<Void?> {
        return favorites.removeOfFavorite(event).then()
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
                loadMusic(link, musicManager.scheduler)
            } else {
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
                    musicManager.player.addListener(musicManager.scheduler)
                    loadMusic(link, musicManager.scheduler)
                } else {
                    musicManager.player.addListener(musicManager.scheduler)
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
            loadItem(input, scheduler)
        } else {
            searchAndLoadItem(input, scheduler)
        }
    }

    private fun isValidYoutubeLink(input: String): Boolean {
        val simpleUrlRegex = "^(https?://)?(www\\.)?\\S+$"
        return input.matches(Regex(simpleUrlRegex))
    }

    private fun loadItem(input: String, scheduler: TrackScheduler): Mono<Void?> {
        playerManager.loadItem(input, scheduler)
        return Mono.empty()
    }

    private fun searchAndLoadItem(input: String, scheduler: TrackScheduler): Mono<Void?> {
        val youtubeSearchResult = youTubeImpl.searchYoutube(input)
        if (youtubeSearchResult != null) {
            loadItem(youtubeSearchResult, scheduler)
        }
        return Mono.empty()
    }


    private fun loadMusic(link: String, scheduler: TrackScheduler): Mono<Void?> {
        return Mono.fromRunnable<Void> {
            try {
                playerManager.loadItem(link, scheduler)
            } catch (e: Exception) {
                println("An error occurred: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun stopMusic(event: MessageCreateEvent, musicManager: GuildMusicManager): Mono<VoiceChannel?> {
        val member = event.member.orElse(null)
        val voiceState = member?.voiceState?.block()
        val channel = voiceState?.channel?.block()

        return if (channel != null) {
            voiceChannelService.disconnect(event).subscribe()
            musicManager.player.removeListener(musicManager.scheduler)
            musicManager.scheduler.clearQueue()
            Mono.just(channel)
        } else {
            Mono.empty()
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