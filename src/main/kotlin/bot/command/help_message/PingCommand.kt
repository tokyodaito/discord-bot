package bot.command.help_message

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.MessageService

class PingCommand(private val messageService: MessageService) : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { messageService.sendMessage(it, "Pong!") }
    }
}