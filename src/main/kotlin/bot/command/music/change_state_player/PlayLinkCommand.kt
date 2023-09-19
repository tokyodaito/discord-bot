package bot.command.music.change_state_player

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

class PlayLinkCommand(private val link: String) : Command {
    private val musicService = Bot.serviceComponent.getMusicService()
    private val messageService = Bot.serviceComponent.getMessageService()

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