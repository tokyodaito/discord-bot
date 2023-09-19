package bot.command.help_message

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono

class HelpCommand : Command {
    val messageService = Bot.serviceComponent.getMessageService()
    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val ping = "ping - проверка работоспособности бота"
        val play = "play (либо ссылка, либо название) - подключение к голосовому каналу и воспроизведение песни"
        val serega = "серега - подключение к голосовому каналу и воспроизведение плейлиста с Серегой Пиратом"
        val stop = "stop - отключение от голосового канала и прекращения воспроизведения музыки"
        val next = "next - переключится на следующий трек"
        val queue = "queue - посмотреть очередь воспроизведения песен"
        val what = "what - какая песня сейчас воспроизводится"
        val loop = "loop - включить циклическое повторение песни"
        val shuffle = "shuffle - перемешать очередь воспроизведения музыки"
        val playlistloop = "playlistloop - включить циклическое повторение плейлиста"
        val jump = "jump - перейти к воспроизведению песни по индексу в очереди"

        return event?.let {
            messageService.createEmbedMessage(
                event,
                title = "Список команд:",
                description = "$ping\n$play\n$serega\n$stop\n$next\n$queue\n$what\n$loop\n$playlistloop\n$shuffle\n$jump"
            ).then()
        }
    }
}