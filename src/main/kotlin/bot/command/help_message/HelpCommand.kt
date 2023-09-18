package bot.command.help_message

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

class HelpCommand : Command {
    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        TODO("Not yet implemented")
    }
}