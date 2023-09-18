package bot.command.music.loop

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono
import service.MessageService

class LoopCommand : Command {
    private val messageService = Bot.serviceComponent.getMessageService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
        val musicManager = GuildManager.getGuildMusicManager(guildId)
        musicManager.scheduler.loop = !musicManager.scheduler.loop

        return if (musicManager.scheduler.currentTrack != null) {
            Mono.fromCallable {}.flatMap {
                if (musicManager.scheduler.loop) messageService.sendMessage(event, "Повтор включен")
                else messageService.sendMessage(event, "Повтор выключен")
            }.flatMap {
                messageService.sendInformationAboutSong(
                    event,
                    musicManager.scheduler.currentTrack!!,
                    musicManager.scheduler.loop,
                    loopPlaylist = false,
                    stayInQueueStatus = false
                )
            }
        } else {
            Mono.empty()
        }
    }
}