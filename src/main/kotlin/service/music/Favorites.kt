package service.music

import bot.Bot
import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.event.domain.message.ReactionAddEvent
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.reaction.ReactionEmoji
import manager.GuildManager.getGuildMusicManager
import manager.GuildMusicManager
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

internal class Favorites {
    private val messageService = Bot.serviceComponent.getMessageService()
    private val databaseImpl = Bot.databaseComponent.getDatabaseImpl()
    private val youTubeImpl = Bot.remoteComponent.getYouTubeImpl()

    fun saveFavorite(event: MessageCreateEvent): Mono<Void?> {
        val userId = extractUserId(event) ?: return sendErrorMessage(event, "Неправильная ссылка").then()

        return extractContentFromEvent(event)
            .flatMap { content ->
                val normalized = normalizeYoutubeLink(content) ?: return@flatMap sendErrorMessage(event, "Неправильная ссылка")
                databaseImpl.addFavorite(userId, normalized).then(sendMessage(event, "Трек добавлен в избранное"))
            }
            .switchIfEmpty(sendErrorMessage(event, "Неправильная ссылка"))
            .onErrorResume {
                println("Error saveFavorites: $it")
                Mono.empty()
            }
            .then()
    }

    fun getFavoritesToDisplay(event: MessageCreateEvent): Mono<Void?> {
        val memberId = extractUserId(event) ?: return Mono.empty()

        return getFavorites(memberId)
            .flatMap { favorites ->
                val list = favorites.orEmpty()
                if (list.isEmpty()) sendMessage(event, "У вас нет сохраненных песен").then()
                else displayFavorites(event, list)
            }
            .onErrorResume {
                logError("Error in getFavoritesToDisplay: $it")
                sendErrorMessage(event, "Возникла ошибка, пожалуйста, свяжитесь с поддержкой").then()
            }
    }

    fun getLinkOfFavorite(event: MessageCreateEvent): Mono<String?> {
        val memberId = extractUserId(event) ?: return Mono.empty()

        val index = extractIndex(event.message.content)
        if (index == null || index < 1) {
            return sendErrorMessage(event, "Неправильный индекс").then(Mono.empty())
        }

        return getFavoriteLink(memberId, index, event)
    }

    fun removeFromFavorite(event: MessageCreateEvent): Mono<Void> {
        val memberId = extractUserId(event) ?: return Mono.empty()

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
        val musicManager = getGuildMusicManager(event) ?: return Mono.empty()

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

    private fun normalizeYoutubeLink(input: String): String? {
        val trimmed = input.trim()
        if (!isYoutubeLink(trimmed)) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    private fun getFavorites(memberId: Snowflake): Mono<List<String>?> =
        databaseImpl.getFavorites(memberId)

    private fun displayFavorites(event: MessageCreateEvent, favorites: List<String>): Mono<Void> {
        val favoritesPerPage = 10
        val totalPages = (favorites.size + favoritesPerPage - 1) / favoritesPerPage
        val authorId = extractUserId(event) ?: return Mono.empty()
        var currentPage = 0

        return buildFavoritesPageDescription(favorites, currentPage, favoritesPerPage)
            .flatMap { description ->
                messageService.createEmbedMessage(
                    event,
                    "Твои избранные треки (Страница ${currentPage + 1} из $totalPages)",
                    description
                )
            }
            .flatMap { message ->
                if (totalPages > 1) {
                    addPageControlsAndHandleReactions(authorId, message, totalPages) { newPage ->
                        currentPage = newPage
                        buildFavoritesPageDescription(favorites, currentPage, favoritesPerPage)
                            .flatMap { description ->
                                messageService.editEmbedMessage(
                                    message,
                                    "Твои избранные треки (Страница ${currentPage + 1} из $totalPages)",
                                    description
                                )
                            }
                            .then(message.removeUserReactions(authorId))
                    }
                } else {
                    Mono.empty()
                }
            }
    }

    private fun logError(error: String) {
        println(error)
    }

    private fun formatFavoritesPage(favorites: List<String>, page: Int, favoritesPerPage: Int): List<String> {
        val startIndex = page * favoritesPerPage
        val endIndex = Integer.min(startIndex + favoritesPerPage, favorites.size)
        return favorites.subList(startIndex, endIndex).mapIndexed { index, favorite ->
            "${startIndex + index + 1}. $favorite"
        }
    }

    private fun buildFavoritesPageDescription(favorites: List<String>, page: Int, favoritesPerPage: Int): Mono<String> {
        val pageLines = formatFavoritesPage(favorites, page, favoritesPerPage)
        return Flux.fromIterable(pageLines)
            .flatMap({ line ->
                val link = line.substringAfter(". ").trim()
                Mono.fromCallable { youTubeImpl.fetchInfo(link) ?: link }
                    .subscribeOn(Schedulers.boundedElastic())
                    .map { title -> "${line.substringBefore(". ")}. $title" }
                    .onErrorReturn(line)
            }, 4)
            .collectList()
            .map { it.joinToString("\n") }
    }

    private fun addPageControlsAndHandleReactions(
        authorId: Snowflake,
        message: Message,
        totalPages: Int,
        onPageChanged: (Int) -> Mono<Void>
    ): Mono<Void> {
        var currentPage = 0
        return message.addInitialReactions()
            .then(
                message.listenToReactions(authorId) { direction ->
                    when (direction) {
                        Direction.LEFT -> if (currentPage > 0) currentPage--
                        Direction.RIGHT -> if (currentPage < totalPages - 1) currentPage++
                        Direction.NONE -> {}
                    }
                    onPageChanged(currentPage)
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
                    sendMessage(event, errorMessage).then(Mono.empty())
                } else {
                    Mono.just(favorites[index - 1])
                }
            }
    }

    private fun sendErrorMessage(event: MessageCreateEvent, message: String): Mono<Void> {
        return sendMessage(event, message).then()
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
                        databaseImpl.removeFavorite(memberId, link).then(sendMessage(event, "Трек удален успешно")).then()
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
            val link = "https://www.youtube.com/watch?v=$input"
            databaseImpl.addFavorite(userId, link).then(sendMessage(event, "Трек добавлен в избранное")).then()
        } else {
            sendMessage(event, "Вы ничего не ввели").then()
        }
    }

    private fun Message.addInitialReactions(): Mono<Void> {
        return this.addReaction(ReactionEmoji.unicode("⬅"))
            .then(this.addReaction(ReactionEmoji.unicode("➡")))
            .onErrorResume { Mono.empty() }
    }

    private fun Message.removeUserReactions(authorId: Snowflake): Mono<Void> {
        return this.removeReaction(ReactionEmoji.unicode("⬅"), authorId)
            .then(this.removeReaction(ReactionEmoji.unicode("➡"), authorId))
            .onErrorResume { Mono.empty() }
    }

    private fun Message.listenToReactions(
        authorId: Snowflake,
        onReaction: (Direction) -> Mono<Void>
    ): Mono<Void> {
        return this.client.eventDispatcher.on(ReactionAddEvent::class.java)
            .filter { it.isReactionFromAuthor(this.id, authorId) }
            .take(Duration.ofMinutes(10))
            .flatMap { reactionEvent ->
                onReaction.invoke(reactionEvent.getDirection())
            }.then()
    }

    private fun ReactionAddEvent.isReactionFromAuthor(messageId: Snowflake, authorId: Snowflake): Boolean {
        return this.messageId == messageId &&
                this.userId == authorId &&
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

    private fun sendMessage(event: MessageCreateEvent, message: String): Mono<Void?> {
        return messageService.createEmbedMessage(event, message).then()
    }
}
