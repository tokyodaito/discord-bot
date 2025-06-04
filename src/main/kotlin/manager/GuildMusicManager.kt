package manager

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import discord4j.common.util.Snowflake
import discord4j.voice.AudioProvider
import music.LavaPlayerAudioProvider
import music.TrackScheduler


import model.database.DatabaseImpl
import service.MessageService
import service.VoiceChannelService

class GuildMusicManager(
    private val guildId: Snowflake,
    playerManager: AudioPlayerManager,
    private val databaseImpl: DatabaseImpl,
    messageService: MessageService,
    voiceChannelService: VoiceChannelService,
) {

    @Volatile
    var godMode = false
    val player: AudioPlayer = playerManager.createPlayer()
    val scheduler = TrackScheduler(player, messageService, voiceChannelService)

    val provider: AudioProvider = LavaPlayerAudioProvider(player)

    @Volatile
    private var firstMessage = false

    @Volatile
    private var firstMessageChecked = false

    fun checkExistsGuild(): Boolean {
        if (!firstMessageChecked) {
            firstMessageChecked = true
            databaseImpl.existsGuild(guildId.toString()).doOnNext {
                firstMessage = it
            }.subscribe()
        }
        return firstMessage
    }

    fun addGuild() {
        databaseImpl.addGuild(guildId.toString())
        firstMessage = true
        firstMessageChecked = true
    }
}