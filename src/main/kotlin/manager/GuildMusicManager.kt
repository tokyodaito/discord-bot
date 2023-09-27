package manager

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
}