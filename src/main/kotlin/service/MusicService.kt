package service

import bot.Bot
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.`object`.reaction.ReactionEmoji
import manager.GuildManager.getGuildMusicManager
import manager.GuildManager.playerManager
import reactor.core.publisher.Mono

class MusicService {
    private val youTubeImpl = Bot.remoteComponent.getYouTubeImpl()
    private val messageService = Bot.serviceComponent.getMessageService()
    private val voiceChannelService = Bot.serviceComponent.getVoiceChannelService()

    fun play(event: MessageCreateEvent, link: String): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty<Void>()
        val musicManager = getGuildMusicManager(guildId)
        musicManager.scheduler.currentEvent = event

        return voiceChannelService.join(event)
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


    fun play(event: MessageCreateEvent): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        musicManager.scheduler.currentEvent = event

        return voiceChannelService.join(event)
            .then(
                Mono.justOrEmpty(event.message.content)
                    .map { content -> content.split(" ") }
                    .doOnNext { command ->
                        if (command.size > 1) {
                            val input = command.subList(1, command.size).joinToString(" ")
                            if (!input.matches(Regex("^(https?|ftp)://[^\\s/$.?#].\\S*$"))) {
                                val youtubeSearchResult = youTubeImpl.searchYoutube(input)
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
            .then()
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
                    messageService.sendMessage(event, "Неверный индекс")
                }
            } else {
                messageService.sendMessage(event, "Укажите индекс трека")
            }
        }
    }

    fun stopPlaying(event: MessageCreateEvent): Mono<Void?> {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        val gifLink = "https://media.discordapp.net/attachments/816984360665219113/959247739575750717/bylling.gif"
        val denialGifLink =
            "https://tenor.com/view/%D0%BF%D0%BE%D1%88%C3%AB%D0%BB%D0%BD%D0%B0%D1%85%D1%83%D0%B9-gif-22853707"

        return voiceChannelService.checkUser(event).flatMap { isInSameChannel ->
            if (isInSameChannel) {
                Mono.justOrEmpty(event.member.orElse(null)).flatMap { member ->
                    Mono.justOrEmpty(member?.voiceState?.block()).flatMap { voiceState ->
                        Mono.justOrEmpty(voiceState?.channel?.block()).doOnNext { channel ->
                            channel?.let {
                                voiceChannelService.disconnect(event).subscribe()
                                musicManager.scheduler.clearQueue()
                            }
                        }
                    }
                }.then(
                    messageService.sendMessage(event, "Воспроизведение остановлено")
                ).then(
                    messageService.sendMessage(
                        event,
                        gifLink
                    )
                )
            } else {
                messageService.sendMessage(event, denialGifLink)
            }
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
            val endIndex = Integer.min(startIndex + tracksPerPage, tracks.size)
            return tracks.subList(startIndex, endIndex).mapIndexed { index, track ->
                "${startIndex + index + 1}. ${track.info.title}"
            }.joinToString("\n")
        }

        return event.message.channel.flatMap {
            messageService.createEmbedMessage(
                event,
                "Список песен (Страница ${currentPage + 1} из $totalPages)",
                formatTrackListPage(currentPage)
            ).flatMap { message ->
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
                                    messageService.createEmbedMessage(
                                        event,
                                        "Список песен (Страница ${currentPage + 1} из $totalPages)",
                                        formatTrackListPage(currentPage)
                                    ).then()
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

}