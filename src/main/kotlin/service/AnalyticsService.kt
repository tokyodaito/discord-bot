package service

import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

class AnalyticsService(private val path: Path = Path.of("analytics/messages.log")) {
    fun log(event: MessageCreateEvent): Mono<Void> {
        return Mono.fromRunnable<Void> {
            val guildId = event.guildId.map { it.asString() }.orElse("unknown")
            val author = event.message.author.map { it.username }.orElse("unknown")
            val content = event.message.content
            val line = "${Instant.now()} $guildId $author: $content\n"
            Files.createDirectories(path.parent)
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }.onErrorResume { Mono.empty() }
    }
}
