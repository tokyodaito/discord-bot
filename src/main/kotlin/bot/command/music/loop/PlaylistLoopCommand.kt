package bot.command.music.loop

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono
import service.MessageService

class PlaylistLoopCommand : Command {
    private val messageService = Bot.serviceComponent.getMessageService()

    // TODO
    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
        val musicManager = GuildManager.getGuildMusicManager(guildId)

        return Mono.fromCallable {
            musicManager.scheduler.playlistLoop = !musicManager.scheduler.playlistLoop
        }.flatMap {
            if (musicManager.scheduler.playlistLoop) messageService.sendMessage(
                event,
                "Циклический повтор плейлиста включен"
            )
            else messageService.sendMessage(event, "Циклический повтор плейлиста выключен")
        }
    }
}