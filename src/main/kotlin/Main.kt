import bot.Bot

fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Usage: <bot_token> <youtube_api_key>")
        return
    }

    val botToken = args[0]
    val youtubeApiKey = args[1]

    println(botToken)
    println(youtubeApiKey)

    Bot(botToken, youtubeApiKey)
}