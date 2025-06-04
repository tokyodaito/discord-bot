package bot.command.music.change_state_player

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.music.MusicService

class DeleteCommand(private val musicService: MusicService) : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let {
            musicService.deleteTrack(event)
        }
    }
}