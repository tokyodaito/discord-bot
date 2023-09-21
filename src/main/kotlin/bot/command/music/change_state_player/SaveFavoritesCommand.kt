package bot.command.music.change_state_player

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

class SaveFavoritesCommand : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let { Bot.serviceComponent.getMusicService().saveFavorites(it) }
    }
}