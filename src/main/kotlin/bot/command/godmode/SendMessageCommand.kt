package bot.command.godmode

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.GodmodeService

class SendMessageCommand(private val godmodeService: GodmodeService) : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { Mono.fromCallable { godmodeService.sendMessageFromUser(it) }.then() }
    }
}