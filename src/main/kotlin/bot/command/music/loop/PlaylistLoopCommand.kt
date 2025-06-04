package bot.command.music.loop

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono
import service.MessageService

class PlaylistLoopCommand(private val messageService: MessageService) : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
        val musicManager = GuildManager.getGuildMusicManager(guildId)

        return Mono.fromCallable {
            musicManager.scheduler.playlistLoop = !musicManager.scheduler.playlistLoop
        }.flatMap {
            if (musicManager.scheduler.playlistLoop) messageService.createEmbedMessage(
                event,
                "Циклический повтор плейлиста включен"
            ).then()
            else messageService.createEmbedMessage(event, "Циклический повтор плейлиста выключен").then()
        }
    }
}