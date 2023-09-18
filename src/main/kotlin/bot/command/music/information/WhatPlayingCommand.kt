package bot.command.music.information

import bot.Bot
import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono
import service.MessageService

class WhatPlayingCommand : Command {
    private val messageService = Bot.serviceComponent.getMessageService()

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
        val musicManager = GuildManager.getGuildMusicManager(guildId)

        return musicManager.scheduler.currentTrack?.let { track ->
            messageService.sendEmbedMessage(
                event,
                track,
                loop = false,
                loopPlaylist = false,
                stayInQueueStatus = musicManager.scheduler.loop
            ).then()
        }
    }
}