package manager

import bot.Bot
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import discord4j.common.util.Snowflake
import discord4j.voice.AudioProvider
import music.LavaPlayerAudioProvider
import music.TrackScheduler


class GuildMusicManager(
    private val guildId: Snowflake,
    playerManager: AudioPlayerManager
) {

    @Volatile
    var godMode = false
    val player: AudioPlayer = playerManager.createPlayer()
    val scheduler = TrackScheduler(player)

    val provider: AudioProvider = LavaPlayerAudioProvider(player)

    private val listenerLock = Any()
    @Volatile
    private var schedulerListenerAttached = false

    fun attachSchedulerListener() {
        synchronized(listenerLock) {
            if (schedulerListenerAttached) return
            player.addListener(scheduler)
            schedulerListenerAttached = true
        }
    }

    fun detachSchedulerListener() {
        synchronized(listenerLock) {
            if (!schedulerListenerAttached) return
            player.removeListener(scheduler)
            schedulerListenerAttached = false
        }
    }

    fun dispose() {
        detachSchedulerListener()
        scheduler.clearQueue()
        player.destroy()
    }
}
