package manager

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import discord4j.voice.AudioProvider
import music.LavaPlayerAudioProvider
import music.TrackScheduler

class GuildMusicManager(
    playerManager: AudioPlayerManager
) {
    @Volatile
    public var godMode = false
    public val player: AudioPlayer = playerManager.createPlayer()
    public val scheduler = TrackScheduler(player)
    val provider: AudioProvider = LavaPlayerAudioProvider(player)
}