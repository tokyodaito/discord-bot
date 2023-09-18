package service

import bot.Bot
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager.getGuildMusicManager

class GodmodeService {
    private val messageService = Bot.serviceComponent.getMessageService()

    fun godMode(event: MessageCreateEvent, status: Boolean) {
        val guildId = event.guildId.orElse(null)
        val musicManager = getGuildMusicManager(guildId)

        val senderId = event.message.author.orElse(null)?.id?.asString()
        if (senderId == godModeUserId) {
            musicManager.godMode = status
            if (status)
                messageService.sendMessage(event, "godmode enable").then(
                    messageService.sendMessage(
                        event,
                        "https://media.discordapp.net/attachments/965181691981357056/1009410729406906389/2cabf388b2232cc6b21d42cfb5d30266.gif"
                    )
                ).subscribe()
            else
                messageService.sendMessage(event, "godmode disabled").subscribe()
        } else {
            event.message.channel.flatMap {
                it.createMessage("https://tenor.com/view/%D0%BF%D0%BE%D1%88%C3%AB%D0%BB%D0%BD%D0%B0%D1%85%D1%83%D0%B9-gif-22853707")
            }.subscribe()
        }
    }

    companion object {
        const val godModeUserId = "284039904495927297"
    }
}