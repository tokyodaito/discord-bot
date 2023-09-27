package bot

import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

internal fun interface Command {
    fun execute(event: MessageCreateEvent?): Mono<Void?>?
}