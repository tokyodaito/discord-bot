package bot.command.music.switch_tracks

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

class NextTrackCommand : Command {
    private val messageService = Bot.serviceComponent.getMessageService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let {
            Mono.fromCallable { Bot.serviceComponent.getMusicService().nextTrack(event) }.flatMap {
                if (it) {
                    messageService.createEmbedMessage(event, "Включен следующий трек").then()
                } else {
                    messageService.createEmbedMessage(event, "Очередь пуста").then()
                }
            }
        }
    }
}