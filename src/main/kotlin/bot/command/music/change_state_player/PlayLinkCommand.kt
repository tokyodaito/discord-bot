package bot.command.music.change_state_player

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.music.MusicService
import service.MessageService

class PlayLinkCommand(
    private val link: String,
    private val musicService: MusicService,
    private val messageService: MessageService,
) : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let {
            musicService.play(it, link).then(
                messageService.sendMessage(
                    event,
                    "https://tenor.com/view/pirat-serega-pirat-papich-dance-dancing-gif-17296890"
                )
            )
        }
    }
}