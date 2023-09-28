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

        return run {
            val args = event.message.content.split(" ")
            if (args.size > 1) {
                val index = args[1].toIntOrNull()
                if (index != null && musicManager.scheduler.jumpToTrack(index)) {
                    val track = musicManager.scheduler.getFullTrackList()[index - 1]
                    messageService.sendMessage(event, "Перешёл к треку с индексом $index").then(
                        messageService.sendInformationAboutSong(
                            event,
                            track,
                            musicManager.scheduler.loop,
                            musicManager.scheduler.playlistLoop,
                            false
                        )
                    )
                } else {
                    messageService.sendMessage(event, INVALID_INDEX)
                }
            } else {
                messageService.sendMessage(event, "Укажите индекс трека")
            }
        }
    }

    fun stopPlaying(event: MessageCreateEvent): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        return voiceChannelService.checkUserInVoiceChannelWithBot(event).flatMap { isInSameChannel ->
            if (!isInSameChannel) {
                return@flatMap messageService.sendMessage(event, DENIAL_GIF_LINK)
            }
            stopMusic(event, musicManager).then(
                messageService.sendMessage(event, "Воспроизведение остановлено")
            ).then(
                messageService.sendMessage(event, ACCESS_GIF_LINK)
            )
        }.onErrorResume {
            println("Error stopPlaying: $it")
            Mono.empty()
        }
    }

    fun showTrackList(event: MessageCreateEvent): Mono<Void> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        val tracks = musicManager.scheduler.getFullTrackList()
        val tracksPerPage = 10
        val totalPages = (tracks.size + tracksPerPage - 1) / tracksPerPage
        var currentPage = 0

        fun formatTrackListPage(page: Int): String {
            val startIndex = page * tracksPerPage
            return tracks.drop(startIndex).take(tracksPerPage).mapIndexed { index, track ->
                "${startIndex + index + 1}. ${track.info.title}"
            }.joinToString("\n")
        }

        fun handleReaction(message: Message, reactionEvent: ReactionAddEvent): Mono<Void> {
            val changePage = when (reactionEvent.emoji.asUnicodeEmoji().get().raw) {
                "⬅" -> {
                    if (currentPage > 0) {
                        currentPage--
                        true
                    } else false
                }

                "➡" -> {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                        true
                    } else false
                }

                else -> false
            }

            return if (changePage) {
                messageService.editEmbedMessage(
                    message,
                    "Список песен (Страница ${currentPage + 1} из $totalPages)",
                    formatTrackListPage(currentPage)
                ).thenMany(
                    Flux.fromIterable(listOf("➡", "⬅"))
                        .flatMap { emoji ->
                            message.removeReaction(
                                ReactionEmoji.unicode(emoji),
                                event.message.author.get().id
                            )
                        }
                ).then()
            } else {
                Mono.empty()
            }
        }

        return event.message.channel.flatMap {
            messageService.createEmbedMessage(
                event,
                "Список песен (Страница ${currentPage + 1} из $totalPages)",
                formatTrackListPage(currentPage)
            ).flatMap { message ->
                if (totalPages <= 1) {
                    return@flatMap Mono.empty<Void>()
                }

                message.addReaction(ReactionEmoji.unicode("⬅"))
                    .then(message.addReaction(ReactionEmoji.unicode("➡")))
                    .thenMany(
                        message.client.eventDispatcher.on(ReactionAddEvent::class.java)
                            .filter { it.messageId == message.id }
                            .filter { it.userId == event.message.author.get().id }
                            .filter { it.emoji.asUnicodeEmoji().isPresent }.take(totalPages.toLong())
                            .flatMap { reactionEvent -> handleReaction(message, reactionEvent) }
                    ).then()
            }
        }.onErrorResume {
            println("Error showTrackList: $it")
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
                    messageService.sendMessage(event, "Трек удален")
                } else {
                    messageService.sendMessage(event, INVALID_INDEX)
                }
            } else {
                messageService.sendMessage(event, INVALID_INDEX)
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
        event: MessageCreateEvent,
        musicManager: GuildMusicManager,
        link: String?
    ): Mono<Void?> {
        return voiceChannelService.checkUserInVoiceChannelWithBot(event).flatMap { userInVoice ->
            if (!userInVoice) {
                return@flatMap messageService.sendMessage(event, DENIAL_GIF_LINK)
            }
            if (link != null) {
                loadMusic(link, musicManager.scheduler)
            } else {
                processYoutubeLink(event, musicManager.scheduler)
            }
        }
    }

    private fun processBotNotInServer(
        event: MessageCreateEvent,
        musicManager: GuildMusicManager,
        link: String?
    ): Mono<Void?> {
        return voiceChannelService.checkUserInVoiceChannel(event).flatMap { userInVoice ->
            if (!userInVoice) {
                return@flatMap messageService.sendMessage(event, DENIAL_GIF_LINK)
            }
            voiceChannelService.join(event).then(
                if (link != null) {
                    loadMusic(link, musicManager.scheduler)
                } else {
                    processYoutubeLink(event, musicManager.scheduler)
                }
            )
        }
    }

    private fun processYoutubeLink(event: MessageCreateEvent, scheduler: TrackScheduler): Mono<Void?> {
        return Mono.justOrEmpty(event.message.content).map { content ->
            val command = content.split(" ")
            if (command.size <= 1) return@map
            val input = command.subList(1, command.size).joinToString(" ")
            val youtubeVideoRegex = "^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/)[a-zA-Z0-9_-]+"
            val youtubePlaylistRegex = "^(https?://)?(www\\.)?youtube\\.com/playlist\\?list=[a-zA-Z0-9_-]+"
            if (!input.matches(Regex("$youtubeVideoRegex|$youtubePlaylistRegex"))) {
                val youtubeSearchResult = youTubeImpl.searchYoutube(input)
                if (youtubeSearchResult != null) {
                    playerManager.loadItem(youtubeSearchResult, scheduler)
                }
            } else {
                playerManager.loadItem(input, scheduler)
            }
        }.then()
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
        return Mono.justOrEmpty(event.member.orElse(null)).flatMap { member ->
            Mono.justOrEmpty(member?.voiceState?.block()).flatMap { voiceState ->
                Mono.justOrEmpty(voiceState?.channel?.block()).doOnNext { channel ->
                    channel?.let {
                        voiceChannelService.disconnect(event).subscribe()
                        musicManager.player.removeListener(musicManager.scheduler)
                        musicManager.scheduler.clearQueue()
                    }
                }
            }
        }
    }

    companion object {
        private const val DENIAL_GIF_LINK =
            "https://tenor.com/view/%D0%BF%D0%BE%D1%85%D1%83%D0%B9-death-error-gif-20558982"
        private const val ACCESS_GIF_LINK = "https://tenor.com/view/chatviblyadok-gif-25739796"

        private const val INVALID_INDEX = "Неверный индекс"
    }
}