package bot.command.music.switch_tracks

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono
import service.MessageService

class ShuffleCommand(private val messageService: MessageService) : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
        val musicManager = GuildManager.getGuildMusicManager(guildId)

        return messageService.createEmbedMessage(event, "Очередь перемешана").let {
            Mono.fromCallable { musicManager.scheduler.shuffleQueue() }.then(it)
                .onErrorResume {
                    println("Error shuffleQueue: $it")
                    Mono.empty()
                }.then()
        }
    }
}