package service

import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager.getGuildMusicManager
import service.MessageService

class GodmodeService(private val messageService: MessageService) {

    fun setGodmodeStatus(event: MessageCreateEvent, status: Boolean) {
        val senderId = event.message.author.orElse(null)?.id?.asString()

        if (isGodModeUser(senderId)) {
            updateGodModeStatus(event, status)
        } else {
            sendDenialMessage(event)
        }
    }

    fun sendMessageFromUser(event: MessageCreateEvent) {
        val senderId = event.message.author.orElse(null)?.id?.asString()

        if (isGodModeUser(senderId)) {
            val modifiedContent = event.message.content.removePrefix("!sendmessage").trim()
            messageService.sendMessage(event, modifiedContent).subscribe()
        }
    }


    private fun isGodModeUser(senderId: String?): Boolean {
        return senderId == godModeUserId
    }

    private fun updateGodModeStatus(event: MessageCreateEvent, status: Boolean) {
        val musicManager = getGuildMusicManager(event)
        musicManager.godMode = status

        sendGodModeMessage(event, status)
        sendGodModeGif(event)
    }

    private fun sendGodModeMessage(event: MessageCreateEvent, status: Boolean) {
        val messageToSend = if (status) "godmode enabled" else "godmode disabled"
        messageService.sendMessage(event, messageToSend).subscribe()
    }

    private fun sendGodModeGif(event: MessageCreateEvent) {
        val gifLink =
            "https://media.discordapp.net/attachments/965181691981357056/1009410729406906389/2cabf388b2232cc6b21d42cfb5d30266.gif"
        messageService.sendMessage(event, gifLink).subscribe()
    }

    private fun sendDenialMessage(event: MessageCreateEvent) {
        val denialGifLink =
            "https://tenor.com/view/%D0%BF%D0%BE%D1%88%C3%AB%D0%BB%D0%BD%D0%B0%D1%85%D1%83%D0%B9-gif-22853707"
        messageService.sendMessage(event, denialGifLink).subscribe()
    }

    companion object {
        const val godModeUserId = "284039904495927297"
    }
}