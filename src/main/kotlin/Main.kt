import bot.Bot
import di.appModule
import org.koin.core.context.startKoin

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: <bot_token> <youtube_api_key>")
        return
    }

    val botToken = args[0]
    val youtubeApiKey = args[1]

    val koin = startKoin {
        modules(appModule(youtubeApiKey))
    }.koin

    val database = koin.get<model.database.Database>()
    val musicService = koin.get<service.music.MusicService>()
    val messageService = koin.get<service.MessageService>()
    val godmodeService = koin.get<service.GodmodeService>()

    Bot(botToken, database, musicService, messageService, godmodeService)
}