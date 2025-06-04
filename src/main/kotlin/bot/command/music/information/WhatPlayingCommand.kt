package bot.command.music.information

import bot.Command
import discord4j.core.event.domain.message.MessageCreateEvent
import manager.GuildManager
import reactor.core.publisher.Mono
import service.MessageService

class WhatPlayingCommand(private val messageService: MessageService) : Command {

    override fun execute(event: MessageCreateEvent?): Mono<Void?>? {
        val guildId = event?.guildId?.orElse(null) ?: return Mono.empty()
        val musicManager = GuildManager.getGuildMusicManager(guildId)

        return musicManager.scheduler.currentTrack?.let { track ->
            messageService.sendNewInformationAboutSong(
                event,
                track,
                loop = musicManager.scheduler.loop,
                loopPlaylist = musicManager.scheduler.playlistLoop,
                stayInQueueStatus = false
            ).then()
        }
    }
}