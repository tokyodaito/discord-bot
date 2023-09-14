package bot

import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

internal interface Command {
    fun execute(event: MessageCreateEvent?): Mono<Void?>?
}