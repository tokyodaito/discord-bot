package bot.slash

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.discordjson.json.ApplicationCommandRequest
import reactor.core.publisher.Mono

interface SlashCommand {
    val request: ApplicationCommandRequest
    fun handle(event: ChatInputInteractionEvent): Mono<Void>
}
