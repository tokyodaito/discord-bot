package bot.slash

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.discordjson.json.ApplicationCommandRequest
import reactor.core.publisher.Mono

class RelaySlashCommand(
    override val request: ApplicationCommandRequest,
    private val buildMessage: (ChatInputInteractionEvent) -> String
) : SlashCommand {
    override fun handle(event: ChatInputInteractionEvent): Mono<Void> {
        val commandMessage = buildMessage(event)
        val sendCommand = event.interaction.channel
            .ofType(MessageChannel::class.java)
            .flatMap { channel -> channel.createMessage(commandMessage) }
            .then()
        return event.reply().withContent("Выполняю команду").withEphemeral(true).then(sendCommand)
    }
}
