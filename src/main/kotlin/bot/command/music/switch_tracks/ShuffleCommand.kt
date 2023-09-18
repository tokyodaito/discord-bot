package bot.command.music.switch_tracks

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono
import service.MessageService

class ShuffleCommand : Command {
    private val messageService = Bot.serviceComponent.getMessageService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
        val musicManager = GuildManager.getGuildMusicManager(guildId)

        return messageService.sendMessage(event, "Очередь перемешана").let {
            Mono.fromCallable { musicManager.scheduler.shuffleQueue() }.then(it)
        }
    }
}