package model.database

import bot.Bot
import discord4j.common.util.Snowflake
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.net.URI

class DatabaseImpl {
    fun addFavorite(memberId: Snowflake, link: String): Mono<Void> {
        return Mono.fromCallable {
            if (!isValidUrl(link)) {
                throw IllegalArgumentException("Invalid url")
            }
            saveLinkToDatabase(memberId, link)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .handleError("addFavorite")
            .then()
    }

    fun getFavorites(memberId: Snowflake): Mono<List<String>?> {
        return Mono.fromCallable {
            Bot.databaseComponent.getDatabase().loadServerFavorites(memberId.toString())
        }
            .subscribeOn(Schedulers.boundedElastic())
            .handleError("getFavorites")
    }

    fun removeFavorite(memberId: Snowflake, link: String): Mono<Void> {
        return Mono.fromCallable { Bot.databaseComponent.getDatabase().removeServerFavorite(memberId.toString(), link) }
            .subscribeOn(Schedulers.boundedElastic())
            .handleError("removeFavorite")
            .then()
    }

    fun addGuild(guildId: String): Mono<Void> {
        return Mono.fromCallable { Bot.databaseComponent.getDatabase().addGuild(guildId) }
            .subscribeOn(Schedulers.boundedElastic())
            .handleError("addGuild")
            .then()
    }

    fun existsGuild(guildId: String): Mono<Boolean> {
        return Mono.fromCallable {
            Bot.databaseComponent.getDatabase().existsGuild(guildId)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .handleError("existsGuild")
            .defaultIfEmpty(false)
    }

    private fun isValidUrl(link: String): Boolean {
        val uri = runCatching { URI(link) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    private fun saveLinkToDatabase(memberId: Snowflake, link: String) {
        Bot.databaseComponent.getDatabase().saveServerFavorites(memberId.toString(), link)
    }

    private fun <T> Mono<T>.handleError(functionName: String): Mono<T> {
        return this.onErrorResume {
            println("Error $functionName: $it")
            Mono.empty()
        }
    }
}
