package service.music

import bot.Bot
import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import manager.GuildManager.getGuildMusicManager
import reactor.core.publisher.Mono

internal class Favorites {
    private val messageService = Bot.serviceComponent.getMessageService()
    private val databaseImpl = Bot.databaseComponent.getDatabaseImpl()

    fun saveFavorite(event: MessageCreateEvent): Mono<Void?> {
        val userId = event.message.author.map { it.id }.orElse(null)

        return Mono.justOrEmpty(event.message.content)
            .map { content -> content.split(" ") }
            .doOnNext { command ->
                if (command.size > 1) {
                    val input = command.subList(1, command.size).joinToString(" ")
                    val youtubeVideoRegex = "^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/)[a-zA-Z0-9_-]+"
                    val youtubePlaylistRegex = "^(https?://)?(www\\.)?youtube\\.com/playlist\\?list=[a-zA-Z0-9_-]+"

                    if (input.matches(Regex("$youtubeVideoRegex|$youtubePlaylistRegex"))) {
                        if (userId != null) {
                            databaseImpl.addFavorite(userId, input)
                            messageService.sendMessage(event, "Трек добавлен в избранное").subscribe()
                        } else {
                            println("Error userId: $userId")
                        }
                    } else {
                        messageService.sendMessage(event, "Неправильная ссылка").subscribe()
                    }
                }
            }.onErrorResume {
                println("Error saveFavorites: $it")
                Mono.empty()
            }.then()
    }

    fun getFavoritesToDisplay(event: MessageCreateEvent): Mono<Void?> {
        val memberId = event.message.author.map { it.id }.orElse(null) ?: return Mono.empty()

        val favoritesPerPage = 10
        var currentPage = 0

        return databaseImpl.getFavorites(memberId)
            .flatMap { favoritesList ->
                if (favoritesList.isNullOrEmpty()) {
                    messageService.sendMessage(event, "У вас нет сохраненных песен").then()
                } else {
                    val totalPages = (favoritesList.size + favoritesPerPage - 1) / favoritesPerPage

                    event.message.channel.flatMap {
                        messageService.createEmbedMessage(
                            event,
                            "Твои избранные треки (Страница ${currentPage + 1} of $totalPages)",
                            formatFavoritesPage(favoritesList, currentPage, favoritesPerPage)
                        ).flatMap { message ->
                            if (totalPages > 1) {
                                addPageControlsAndHandleReactions(
                                    event,
                                    message,
                                    totalPages,
                                    favoritesList,
                                    favoritesPerPage
                                )
                            } else {
                                Mono.empty()
                            }
                        }
                    }
                }
            }
            .onErrorResume {
                println("Error in getFavoritesToDisplay: $it")
                messageService.sendMessage(event, "Возникла какая-то ошибка. Хуй знает, стучись к Боде").then()
            }
    }


    private fun formatFavoritesPage(favorites: List<String>, page: Int, favoritesPerPage: Int): String {
        val startIndex = page * favoritesPerPage
        val endIndex = Integer.min(startIndex + favoritesPerPage, favorites.size)
        return favorites.subList(startIndex, endIndex).mapIndexed { index, favorite ->
            "${startIndex + index + 1}. ${
                Bot.remoteComponent.getYouTubeImpl().fetchInfo(favorite) ?: "Unknown Title"
            }"
        }.joinToString("\n")
    }

    private fun addPageControlsAndHandleReactions(
        event: MessageCreateEvent,
        message: Message,
        totalPages: Int,
        favoritesList: List<String>,
        favoritesPerPage: Int
    ): Mono<Void> {
        var currentPage = 0
        return message.addReaction(ReactionEmoji.unicode("⬅"))
            .then(message.addReaction(ReactionEmoji.unicode("➡")))
            .then(
                message.client.eventDispatcher.on(ReactionAddEvent::class.java)
                    .filter { it.messageId == message.id }
                    .filter { it.userId == event.message.author.get().id }
                    .filter { it.emoji.asUnicodeEmoji().isPresent }.take(totalPages.toLong())
                    .flatMap { reactionEvent ->
                        val changePage = when (reactionEvent.emoji.asUnicodeEmoji().get()) {
                            ReactionEmoji.unicode("⬅") -> if (currentPage > 0) {
                                currentPage--
                                true
                            } else false

                            ReactionEmoji.unicode("➡") -> if (currentPage < totalPages - 1) {
                                currentPage++
                                true
                            } else false

                            else -> false
                        }

                        if (changePage) {
                            messageService.editEmbedMessage(
                                message,
                                "Твои избранные треки (Страница ${currentPage + 1} of $totalPages)",
                                formatFavoritesPage(favoritesList, currentPage, favoritesPerPage)
                            ).then(
                                message.removeReaction(
                                    ReactionEmoji.unicode("➡"),
                                    event.message.author.get().id
                                )
                            ).then(
                                message.removeReaction(
                                    ReactionEmoji.unicode("⬅"),
                                    event.message.author.get().id
                                )
                            ).then()
                        } else {
                            Mono.empty()
                        }
                    }.then()
            )
    }

    fun getLinkOfFavorite(event: MessageCreateEvent): Mono<String?> {
        val memberId: Snowflake? = event.message.author.map { it.id }.orElse(null)

        return if (memberId != null) {
            val content = event.message.content
            val index = content?.split(" ")?.getOrNull(1)?.toIntOrNull()

            if (index == null || index < 1) {
                messageService.sendMessage(event, "Неправильный индекс").thenReturn(null)
            } else {
                databaseImpl.getFavorites(memberId)
                    .flatMap { favorites ->
                        if (favorites.isNullOrEmpty() || index > favorites.size) {
                            Mono.just("Неправильный индекс или не найден трек")
                                .flatMap { message -> messageService.sendMessage(event, message).thenReturn(null) }
                        } else {
                            val link = favorites[index - 1]
                            Mono.just(link)
                        }
                    }
            }
        } else {
            Mono.empty()
        }
    }

    fun removeOfFavorite(event: MessageCreateEvent): Mono<Void> {
        val memberId: Snowflake? = event.message.author.map { it.id }.orElse(null)

        return if (memberId != null) {
            val content = event.message.content
            val index = content.split(" ").getOrNull(1)?.toIntOrNull()

            if (index == null || index < 1) {
                messageService.sendMessage(event, "Неправильный индекс").then()
            } else {
                databaseImpl.getFavorites(memberId)
                    .flatMap { favorites ->
                        if (favorites.isNullOrEmpty() || index > favorites.size) {
                            messageService.sendMessage(event, "Неправильный индекс или не найден трек").then()
                        } else {
                            val link = favorites[index - 1]
                            databaseImpl.removeFavorite(memberId, link)
                                .then(messageService.sendMessage(event, "Трек удален успешно"))
                        }
                    }
            }
        } else {
            Mono.empty()
        }
    }

    fun addTheCurrentTrackToFavorites(event: MessageCreateEvent): Mono<Void> {
        val userId = event.message.author.map { it.id }.orElse(null)
        val musicManager = getGuildMusicManager(event)

        val denialGifLink =
            "https://tenor.com/view/%D0%BF%D0%BE%D1%85%D1%83%D0%B9-death-error-gif-20558982"

        return Mono.justOrEmpty(event.message.content)
            .doOnNext {
                val input = musicManager.scheduler.currentTrack?.info?.identifier
                if (input != null) {
                    if (userId != null) {
                        databaseImpl.addFavorite(userId, "https://www.youtube.com/watch?v=$input")
                        messageService.sendMessage(event, "Трек добавлен в избранное").subscribe()
                    } else {
                        println("Error userId: $userId")
                    }
                } else {
                    messageService.sendMessage(event, denialGifLink).subscribe()
                }
            }.onErrorResume {
                println("Error saveFavorites: $it")
                Mono.empty()
            }.then()
    }
}