package service

import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class AnalyticsService(private val root: Path = Path.of("analytics")) {
    fun log(event: MessageCreateEvent): Mono<Void> {
        return Mono.fromRunnable<Void> {
            val guildId = event.guildId.map { it.asString() }.orElse("unknown")
            val channelId = event.message.channelId.asString()
            val author = event.message.author.map { it.username }.orElse("unknown")
            val content = event.message.content
            val line = "${Instant.now()} $author: $content\n"
            val file = root.resolve(guildId).resolve("${channelId}.log")
            Files.createDirectories(file.parent)
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }.onErrorResume { Mono.empty() }
    }
}
