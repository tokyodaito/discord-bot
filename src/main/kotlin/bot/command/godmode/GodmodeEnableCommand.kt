package bot.command.godmode

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.GodmodeService

class GodmodeEnableCommand: Command {
    private val godmodeService = GodmodeService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { Mono.fromCallable { godmodeService.godMode(it, true) }.then() }
    }
}