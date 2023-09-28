package model.database

import bot.Bot
import discord4j.common.util.Snowflake
import reactor.core.publisher.Mono

class DatabaseImpl {
    fun addFavorite(memberId: Snowflake, link: String) {
        Mono.fromCallable {
            if (isValidURL(link)) {
                saveLinkToDatabase(memberId, link)
            } else {
                println("Provided link is invalid.")
            }
        }.handleError("addFavorite")
            .subscribe()
    }

    fun getFavorites(memberId: Snowflake): Mono<List<String>?> {
        return Mono.fromCallable {
            Bot.databaseComponent.getDatabase().loadServerFavorites(memberId.toString())
        }.handleError("getFavorites")
    }

    fun removeFavorite(memberId: Snowflake, link: String): Mono<Void> {
        return Mono.fromCallable { Bot.databaseComponent.getDatabase().removeServerFavorite(memberId.toString(), link) }
            .handleError("removeFavorite")
            .then()
    }

    private fun isValidURL(link: String): Boolean {
        val regex = "^(https?|ftp)://[^\\s/$.?#].\\S*$"
        return link.matches(Regex(regex))
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