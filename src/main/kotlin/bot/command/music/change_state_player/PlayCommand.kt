package bot.command.music.change_state_player

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.MusicService

class PlayCommand : Command {
    private val musicService = Bot.serviceComponent.getMusicService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { musicService.play(it) }
    }
}