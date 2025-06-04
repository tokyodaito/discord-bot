package bot.command.help_message

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import service.MessageService

class HelpCommand(private val messageService: MessageService) : Command {
    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val generalCommands = """
        **Общие команды**
        - `ping`: проверка работоспособности бота
    """.trimIndent()

        val musicCommands = """
        **Музыкальные команды**
        - `play (либо ссылка, либо название)`: подключение и воспроизведение песни
        - `serega`: воспроизведение плейлиста с Серегой Пиратом
        - `stop`: отключение от голосового канала
        - `next`: следующий трек
        - `queue`: посмотреть очередь
        - `what`: какая песня сейчас играет
        - `loop`: циклическое повторение песни
        - `shuffle`: перемешать очередь
        - `playlistloop`: повторение плейлиста
        - `jump (номер)`: перейти к треку в очереди
    """.trimIndent()

        val favoriteCommands = """
        **Команды для работы с избранным**
        - `savefavorite (ссылка)`: сохранить в избранное
        - `getfavorites`: список избранного
        - `pfavorite (номер)`: воспроизвести из избранного
        - `rmfavorite (номер)`: удалить из избранного
        - `nowfavorite`: добавить текущую песню в избранное
    """.trimIndent()

        val allCommands = """
        $generalCommands
        
        $musicCommands
        
        $favoriteCommands
    """.trimIndent()

        return event?.let {
            messageService.createEmbedMessage(
                event,
                title = "Список команд:",
                description = allCommands
            ).then()
        }
    }

}