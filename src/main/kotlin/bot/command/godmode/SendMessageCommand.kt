package bot.command.godmode

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.GodmodeService

class SendMessageCommand : Command {
    private val godmodeService = GodmodeService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { godmodeService.sendMessageFromUser(it).then() } ?: Mono.empty()
    }
}
