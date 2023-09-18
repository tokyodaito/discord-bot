package bot.command.music.switch_tracks

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono
import service.MessageService

class NextTrackCommand : Command {
    private val messageService = Bot.serviceComponent.getMessageService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
        val musicManager = GuildManager.getGuildMusicManager(guildId)
        musicManager.scheduler.loop = false

        return messageService.sendMessage(event, "Включен следующий трек").let {
            Mono.fromCallable { musicManager.scheduler.nextTrack() }.then(it)
        }
    }
}