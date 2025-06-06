package bot.command.music.change_state_player.favorites

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

class RemoveFromFavoriteCommand : Command {
    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { Bot.serviceComponent.getMusicService().removeFromFavorite(it) }
    }
}