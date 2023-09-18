package bot.command.music.switch_tracks

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.MusicService

class JumpCommand : Command {
    private val musicService = Bot.serviceComponent.getMusicService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { musicService.jump(it) }
    }
}