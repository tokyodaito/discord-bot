package bot.command.music.change_state_player

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.MessageService
import service.MusicService

class PlaylistSeregaCommand : Command {
    private val musicService = Bot.serviceComponent.getMusicService()
    private val messageService = Bot.serviceComponent.getMessageService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let {
            musicService.play(it, SEREGA_PIRAT).then(
                messageService.sendMessage(
                    event,
                    "https://tenor.com/view/pirat-serega-pirat-papich-dance-dancing-gif-17296890"
                )
            )
        }
    }

    companion object {
        const val SEREGA_PIRAT = "https://www.youtube.com/watch?v=KhX3T_NYndo&list=PLaxxU3ZabospOFUVjRWofD-mYOQfCxpzw"
    }
}