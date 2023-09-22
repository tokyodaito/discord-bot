package model.database

import bot.Bot
import discord4j.common.util.Snowflake
import reactor.core.publisher.Mono

class DatabaseImpl {
    fun addFavorite(memberId: Snowflake, link: String) {
        Mono.fromCallable {
            if (link.matches(Regex("^(https?|ftp)://[^\\s/$.?#].\\S*$"))) {
                Bot.databaseComponent.getDatabase().saveServerFavorites(memberId.toString(), link)
            } else {
                println("link dont enogouf link")
            }
        }.onErrorResume {
            println("Error addFavorites: $it")
            Mono.empty()
        }.subscribe()
    }

    fun getFavorites(memberId: Snowflake): Mono<List<String>?> {
        return Mono.fromCallable {
            Bot.databaseComponent.getDatabase().loadServerFavorites(memberId.toString())
        }.onErrorResume { e ->
            println("Error addFavorites: $e")
            Mono.empty()
        }
    }

    fun removeFavorite(memberId: Snowflake, link: String): Mono<Void> {
        return Mono.fromCallable { Bot.databaseComponent.getDatabase().removeServerFavorite(memberId.toString(), link) }
            .onErrorResume { e ->
                println("Error removeFavorite: $e")
                Mono.empty()
            }.then()
    }
}