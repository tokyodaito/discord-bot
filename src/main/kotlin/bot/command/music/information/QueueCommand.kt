package bot.command.music.information

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.music.MusicService

class QueueCommand(private val musicService: MusicService) : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { musicService.showTrackList(it).then() }
    }
}