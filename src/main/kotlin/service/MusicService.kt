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

    // TODO
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

    // TODO
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
            }
            .then()
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
                    messageService.sendMessage(event, "Перешёл к треку с индексом $index").then(
                        messageService.sendEmbedMessage(
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

    // TODO
    fun stopPlaying(event: MessageCreateEvent): Mono<Void?> {
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
            messageService.sendMessage(event, "Воспроизведение остановлено")
        ).then(
            messageService.sendMessage(
                event,
                "https://media.discordapp.net/attachments/816984360665219113/959247739575750717/bylling.gif"
            )
        )
    }


    // TODO
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
}