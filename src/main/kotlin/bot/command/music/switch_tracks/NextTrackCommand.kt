package bot.command.music.switch_tracks

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.MessageService
import service.music.MusicService

class NextTrackCommand(
    private val messageService: MessageService,
    private val musicService: MusicService,
) : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        return event?.let {
            Mono.fromCallable { musicService.nextTrack(event) }.flatMap {
                if (it) {
                    messageService.createEmbedMessage(event, "Включен следующий трек").then()
                } else {
                    messageService.createEmbedMessage(event, "Очередь пуста").then()
                }
            }
        }
    }
}