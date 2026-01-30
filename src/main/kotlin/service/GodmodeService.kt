package service

import bot.Bot
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager.getGuildMusicManager
import reactor.core.publisher.Mono

class GodmodeService {
    private val messageService = Bot.serviceComponent.getMessageService()

    fun setGodmodeStatus(event: MessageCreateEvent, status: Boolean): Mono<Void> {
        val senderId = event.message.author.orElse(null)?.id?.asString()

        return if (isGodModeUser(senderId)) updateGodModeStatus(event, status) else sendDenialMessage(event)
    }

    fun sendMessageFromUser(event: MessageCreateEvent): Mono<Void> {
        val senderId = event.message.author.orElse(null)?.id?.asString()

        if (!isGodModeUser(senderId)) return Mono.empty()

        val modifiedContent = event.message.content.removePrefix("!sendmessage").trim()
        if (modifiedContent.isBlank()) return Mono.empty()

        return messageService.sendMessage(event, modifiedContent).then()
    }


    private fun isGodModeUser(senderId: String?): Boolean {
        return senderId == godModeUserId
    }

    private fun updateGodModeStatus(event: MessageCreateEvent, status: Boolean): Mono<Void> {
        val musicManager = getGuildMusicManager(event) ?: return Mono.empty()
        musicManager.godMode = status

        return sendGodModeMessage(event, status).then(sendGodModeGif(event)).then()
    }

    private fun sendGodModeMessage(event: MessageCreateEvent, status: Boolean): Mono<Void> {
        val messageToSend = if (status) "godmode enabled" else "godmode disabled"
        return messageService.sendMessage(event, messageToSend).then()
    }

    private fun sendGodModeGif(event: MessageCreateEvent): Mono<Void> {
        val gifLink =
            "https://media.discordapp.net/attachments/965181691981357056/1009410729406906389/2cabf388b2232cc6b21d42cfb5d30266.gif"
        return messageService.sendMessage(event, gifLink).then()
    }

    private fun sendDenialMessage(event: MessageCreateEvent): Mono<Void> {
        val denialGifLink =
            "https://tenor.com/view/%D0%BF%D0%BE%D1%88%C3%AB%D0%BB%D0%BD%D0%B0%D1%85%D1%83%D0%B9-gif-22853707"
        return messageService.sendMessage(event, denialGifLink).then()
    }

    companion object {
        const val godModeUserId = "284039904495927297"
    }
}
