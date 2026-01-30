package bot.command.music.loop

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono

class LoopCommand : Command {
    private val messageService = Bot.serviceComponent.getMessageService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
        val musicManager = GuildManager.getGuildMusicManager(guildId)
        musicManager.scheduler.loop = !musicManager.scheduler.loop

        val track = musicManager.scheduler.currentTrack ?: return Mono.empty()
        val statusMessage = if (musicManager.scheduler.loop) "\uD83D\uDD0A Повтор включен" else "\uD83D\uDD07 Повтор выключен"

        return messageService.createEmbedMessage(event, statusMessage)
            .then(
                messageService.sendInformationAboutSong(
                    event,
                    track,
                    musicManager.scheduler.loop,
                    loopPlaylist = false,
                    stayInQueueStatus = false
                )
            )
    }
}
