package bot.command.music.information

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

class QueueCommand : Command {
    private val musicService = Bot.serviceComponent.getMusicService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { musicService.showTrackList(it).then() }
    }
}