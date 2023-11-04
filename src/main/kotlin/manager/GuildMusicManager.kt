package manager

import bot.Bot
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import discord4j.common.util.Snowflake
import discord4j.voice.AudioProvider
import music.LavaPlayerAudioProvider
import music.TrackScheduler

class GuildMusicManager(
    val guildId: Snowflake,
    playerManager: AudioPlayerManager
) {
    @Volatile
    var godMode = false
    val player: AudioPlayer = playerManager.createPlayer()
    val scheduler = TrackScheduler(player)
    val provider: AudioProvider = LavaPlayerAudioProvider(player)

    @Volatile
    private var firstMessage = false

    @Volatile
    private var firstMessageChecked = false

    fun checkFirstMessage(): Boolean {
        if (!firstMessageChecked) {
            firstMessageChecked = true
            Bot.databaseComponent.getDatabaseImpl().getFirstMessage(guildId.toString()).doOnNext {
                firstMessage = it
            }
        }
        return firstMessage
    }

    fun updateFirstMessage(statusFirstMessage: Boolean) {
        Bot.databaseComponent.getDatabaseImpl().addGuild(guildId.toString(), statusFirstMessage)
        firstMessage = statusFirstMessage
        firstMessageChecked = true
    }
}