package model.database

import discord4j.common.util.Snowflake
import reactor.core.publisher.Mono
class DatabaseImpl(private val database: Database) {
    fun addFavorite(memberId: Snowflake, link: String) {
        Mono.fromCallable {
            if (isValidURL(link)) {
                saveLinkToDatabase(memberId, link)
            } else {
                println("Provided link is invalid.")
            }
        }.handleError("addFavorite").subscribe()
    }

    fun getFavorites(memberId: Snowflake): Mono<List<String>?> {
        return Mono.fromCallable {
            database.loadServerFavorites(memberId.toString())
        }.handleError("getFavorites")
    }

    fun removeFavorite(memberId: Snowflake, link: String): Mono<Void> {
        return Mono.fromCallable { database.removeServerFavorite(memberId.toString(), link) }
            .handleError("removeFavorite").then()
    }

    fun addGuild(guildId: String) {
        Mono.fromCallable {
            database.addGuild(guildId)
        }.handleError("addGuild").subscribe()
    }

    fun existsGuild(guildId: String): Mono<Boolean> {
        return Mono.fromCallable {
            database.existsGuild(guildId)
        }.handleError("getFirstMessage")
    }

    private fun isValidURL(link: String): Boolean {
        val regex = "^(https?|ftp)://[^\\s/$.?#].\\S*$"
        return link.matches(Regex(regex))
    }

    private fun saveLinkToDatabase(memberId: Snowflake, link: String) {
        database.saveServerFavorites(memberId.toString(), link)
    }

    private fun <T> Mono<T>.handleError(functionName: String): Mono<T> {
        return this.onErrorResume {
            println("Error $functionName: $it")
            Mono.empty()
        }
    }
}