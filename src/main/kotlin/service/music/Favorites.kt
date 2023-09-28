package service.music

import bot.Bot
import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import manager.GuildManager.getGuildMusicManager
import manager.GuildMusicManager
import reactor.core.publisher.Mono

internal class Favorites {
    private val messageService = Bot.serviceComponent.getMessageService()
    private val databaseImpl = Bot.databaseComponent.getDatabaseImpl()

    fun saveFavorite(event: MessageCreateEvent): Mono<Void?> {
        val userId = extractUserId(event)

        return extractContentFromEvent(event)
            .flatMap { content ->
                if (isYoutubeLink(content) && userId != null) {
                    addToFavorites(userId, content)
                    messageService.sendMessage(event, "Трек добавлен в избранное")
                } else {
                    sendErrorMessage(event, "Неправильная ссылка")
                }
            }
            .onErrorResume {
                println("Error saveFavorites: $it")
                Mono.empty()
            }
            .then()
    }

    fun getFavoritesToDisplay(event: MessageCreateEvent): Mono<Void?> {
        val memberId = event.message.author.map { it.id }.orElse(null) ?: return Mono.empty()

        return getFavorites(memberId)
            .flatMap { favorites ->
                if (favorites?.isEmpty() != false) {
                    messageService.sendMessage(event, "У вас нет сохраненных песен").then()
                } else {
                    displayFavorites(event, favorites)
                }
            }
            .onErrorResume {
                logError("Error in getFavoritesToDisplay: $it")
                sendErrorMessage(event, "Возникла ошибка, пожалуйста, свяжитесь с поддержкой").then()
            }
    }

    fun getLinkOfFavorite(event: MessageCreateEvent): Mono<String?> {
        val memberId = event.message.author.map { it.id }.orElse(null) ?: return Mono.empty()

        val index = extractIndex(event.message.content)
        if (index == null || index < 1) {
            return sendErrorMessage(event, "Неправильный индекс").thenReturn(null.toString())
        }

        return getFavoriteLink(memberId, index, event)
    }

    fun removeOfFavorite(event: MessageCreateEvent): Mono<Void> {
        val memberId = event.message.author.map { it.id }.orElse(null) ?: return Mono.empty()

        val content = event.message.content
        val index = content.split(" ").getOrNull(1)?.toIntOrNull()

        return when {
            index == null || index < 1 -> {
                sendErrorMessage(event, "Неправильный индекс")
            }

            else -> {
                removeFavoriteAtIndex(memberId, index, event)
            }
        }
    }

    fun addTheCurrentTrackToFavorites(event: MessageCreateEvent): Mono<Void> {
        val userId = extractUserId(event)
        val musicManager = getGuildMusicManager(event)

        return Mono.justOrEmpty(event.message.content)
            .flatMap { handleAddingToFavorites(userId, musicManager, event) }
            .onErrorResume {
                println("Error saveFavorites: $it")
                Mono.empty()
            }
    }

    private fun extractUserId(event: MessageCreateEvent): Snowflake? {
        return event.message.author.map { it.id }.orElse(null)
    }

    private fun extractContentFromEvent(event: MessageCreateEvent): Mono<String> {
        return Mono.justOrEmpty(event.message.content)
            .map { content -> content.split(" ") }
            .filter { it.size > 1 }
            .map { it.subList(1, it.size).joinToString(" ") }
    }

    private fun isYoutubeLink(input: String): Boolean {
        val youtubeVideoRegex = "^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/)[a-zA-Z0-9_-]+"
        val youtubePlaylistRegex = "^(https?://)?(www\\.)?youtube\\.com/playlist\\?list=[a-zA-Z0-9_-]+"
        return input.matches(Regex("$youtubeVideoRegex|$youtubePlaylistRegex"))
    }

    private fun addToFavorites(userId: Snowflake, input: String) {
        databaseImpl.addFavorite(userId, input)
    }

    private fun getFavorites(memberId: Snowflake): Mono<List<String>?> =
        databaseImpl.getFavorites(memberId)

    private fun displayFavorites(event: MessageCreateEvent, favorites: List<String>): Mono<Void> {
        val favoritesPerPage = 10
        val totalPages = (favorites.size + favoritesPerPage - 1) / favoritesPerPage
        val currentPage = 0

        return event.message.channel.flatMap {
            messageService.createEmbedMessage(
                event,
                "Твои избранные треки (Страница ${currentPage + 1} из $totalPages)",
                formatFavoritesPage(favorites, currentPage, favoritesPerPage)
            ).flatMap { message ->
                if (totalPages > 1) {
                    addPageControlsAndHandleReactions(event, message, totalPages, favorites, favoritesPerPage)
                } else {
                    Mono.empty()
                }
            }
        }
    }

    private fun logError(error: String) {
        println(error)
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
        return message.addInitialReactions()
            .then(
                message.listenToReactions(event, totalPages) { direction ->
                    when (direction) {
                        Direction.LEFT -> if (currentPage > 0) currentPage--
                        Direction.RIGHT -> if (currentPage < totalPages - 1) currentPage++
                        Direction.NONE -> {}
                    }
                    messageService.editEmbedMessage(
                        message,
                        "Твои избранные треки (Страница ${currentPage + 1} of $totalPages)",
                        formatFavoritesPage(favoritesList, currentPage, favoritesPerPage)
                    ).then(message.removeUserReactions(event))
                }
            )
    }

    private fun extractIndex(content: String?): Int? {
        return content?.split(" ")?.getOrNull(1)?.toIntOrNull()
    }

    private fun getFavoriteLink(memberId: Snowflake, index: Int, event: MessageCreateEvent): Mono<String?> {
        return databaseImpl.getFavorites(memberId)
            .flatMap { favorites ->
                return@flatMap if (favorites.isNullOrEmpty() || index > favorites.size) {
                    val errorMessage = "Неправильный индекс или не найден трек"
                    messageService.sendMessage(event, errorMessage).thenReturn(null)
                } else {
                    Mono.just(favorites[index - 1])
                }
            }
    }

    private fun sendErrorMessage(event: MessageCreateEvent, message: String): Mono<Void> {
        return messageService.sendMessage(event, message).then()
    }

    private fun removeFavoriteAtIndex(memberId: Snowflake, index: Int, event: MessageCreateEvent): Mono<Void> {
        return databaseImpl.getFavorites(memberId)
            .flatMap { favorites ->
                when {
                    favorites.isNullOrEmpty() || index > favorites.size -> {
                        sendErrorMessage(event, "Неправильный индекс или не найден трек")
                    }

                    else -> {
                        val link = favorites[index - 1]
                        databaseImpl.removeFavorite(memberId, link)
                            .then(messageService.sendMessage(event, "Трек удален успешно"))
                    }
                }
            }
    }

    private fun handleAddingToFavorites(
        userId: Snowflake?,
        musicManager: GuildMusicManager,
        event: MessageCreateEvent
    ): Mono<Void> {
        val input = musicManager.scheduler.currentTrack?.info?.identifier
        return if (input != null && userId != null) {
            databaseImpl.addFavorite(userId, "https://www.youtube.com/watch?v=$input")
            messageService.sendMessage(event, "Трек добавлен в избранное")
        } else {
            val denialGifLink = "https://tenor.com/view/%D0%BF%D0%BE%D1%85%D1%83%D0%B9-death-error-gif-20558982"
            messageService.sendMessage(event, denialGifLink)
        }.then()
    }

    private fun Message.addInitialReactions(): Mono<Void> {
        return this.addReaction(ReactionEmoji.unicode("⬅"))
            .then(this.addReaction(ReactionEmoji.unicode("➡")))
    }

    private fun Message.removeUserReactions(event: MessageCreateEvent): Mono<Void> {
        return this.removeReaction(ReactionEmoji.unicode("⬅"), event.message.author.get().id)
            .then(this.removeReaction(ReactionEmoji.unicode("➡"), event.message.author.get().id))
    }

    private fun Message.listenToReactions(
        event: MessageCreateEvent,
        totalPages: Int,
        onReaction: (Direction) -> Mono<Void>
    ): Mono<Void> {
        return this.client.eventDispatcher.on(ReactionAddEvent::class.java)
            .filter { it.isReactionFromAuthor(event) }
            .take(totalPages.toLong())
            .flatMap { reactionEvent ->
                onReaction.invoke(reactionEvent.getDirection())
            }.then()
    }

    private fun ReactionAddEvent.isReactionFromAuthor(event: MessageCreateEvent): Boolean {
        return this.messageId == event.message.id &&
                this.userId == event.message.author.get().id &&
                this.emoji.asUnicodeEmoji().isPresent
    }

    private fun ReactionAddEvent.getDirection(): Direction {
        return when (this.emoji.asUnicodeEmoji().get()) {
            ReactionEmoji.unicode("⬅") -> Direction.LEFT
            ReactionEmoji.unicode("➡") -> Direction.RIGHT
            else -> Direction.NONE
        }
    }

    enum class Direction {
        LEFT, RIGHT, NONE
    }
}